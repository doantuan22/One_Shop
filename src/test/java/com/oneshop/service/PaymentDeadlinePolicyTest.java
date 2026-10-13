package com.oneshop.service;

import com.oneshop.config.CheckoutProperties;
import com.oneshop.config.PaymentProperties;
import com.oneshop.entity.FulfillmentType;
import com.oneshop.entity.Order;
import com.oneshop.entity.OrderPaymentStatus;
import com.oneshop.entity.OrderStatus;
import com.oneshop.entity.PaymentMethod;
import com.oneshop.entity.Store;
import com.oneshop.service.allocation.DeliveryServiceAreaPolicy;
import com.oneshop.service.allocation.ShippingFeeCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The 15-minute payment deadline (DEC-15) and the fail-closed policy switches, with a controlled clock: nothing here
 * waits for real time.
 */
class PaymentDeadlinePolicyTest {

    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 10, 13, 10, 0, 0);

    private static Clock at(LocalDateTime time) {
        return Clock.fixed(time.toInstant(ZoneOffset.UTC), ZoneId.of("UTC"));
    }

    private static PaymentDeadlinePolicy policy(LocalDateTime now) {
        return new PaymentDeadlinePolicy(new PaymentProperties(), at(now));
    }

    private static Order order(PaymentMethod method, OrderStatus status, OrderPaymentStatus payment) {
        Order order = new Order();
        order.setPaymentMethod(method);
        order.setOrderStatus(status);
        order.setPaymentStatus(payment);
        ReflectionTestUtils.setField(order, "createdAt", CREATED);
        return order;
    }

    @Test
    @DisplayName("Deadline is created_at + 15 minutes and reconciliation deadline another 15")
    void deadlines() {
        Order order = order(PaymentMethod.ONLINE, OrderStatus.PENDING_PAYMENT, OrderPaymentStatus.UNPAID);
        PaymentDeadlinePolicy policy = policy(CREATED);
        assertThat(policy.paymentDeadline(order)).isEqualTo(CREATED.plusMinutes(15));
        assertThat(policy.reconciliationDeadline(order)).isEqualTo(CREATED.plusMinutes(30));
    }

    @Test
    @DisplayName("Payable strictly before the deadline; not at or after it")
    void boundary() {
        Order order = order(PaymentMethod.ONLINE, OrderStatus.PENDING_PAYMENT, OrderPaymentStatus.UNPAID);
        assertThat(policy(CREATED.plusMinutes(14).plusSeconds(59)).isPayableNow(order)).isTrue();
        assertThat(policy(CREATED.plusMinutes(15)).isPayableNow(order)).isFalse();
        assertThat(policy(CREATED.plusMinutes(15)).isExpired(order)).isTrue();
        assertThat(policy(CREATED.plusMinutes(16)).isPayableNow(order)).isFalse();
    }

    @Test
    @DisplayName("The deadline does not move with the clock, retries or time of reading")
    void deadlineIsFixed() {
        Order order = order(PaymentMethod.ONLINE, OrderStatus.PENDING_PAYMENT, OrderPaymentStatus.UNPAID);
        LocalDateTime first = policy(CREATED.plusMinutes(1)).paymentDeadline(order);
        LocalDateTime later = policy(CREATED.plusMinutes(40)).paymentDeadline(order);
        assertThat(later).isEqualTo(first);
    }

    @Test
    @DisplayName("COD and PAY_AT_STORE have no payment deadline")
    void onlyOnlineHasADeadline() {
        Order cod = order(PaymentMethod.COD, OrderStatus.CONFIRMED, OrderPaymentStatus.UNPAID);
        assertThat(policy(CREATED).paymentDeadline(cod)).isNull();
        assertThat(policy(CREATED.plusDays(3)).isPayableNow(cod)).isFalse();
        assertThat(policy(CREATED.plusDays(3)).isExpired(cod)).isFalse();
    }

    @Test
    @DisplayName("Payment gate: ONLINE is workable only once PAID and past PENDING_PAYMENT")
    void paymentGate() {
        PaymentDeadlinePolicy policy = policy(CREATED);
        assertThat(policy.allowsFulfillmentWork(order(PaymentMethod.ONLINE, OrderStatus.PENDING_PAYMENT, OrderPaymentStatus.UNPAID))).isFalse();
        assertThat(policy.allowsFulfillmentWork(order(PaymentMethod.ONLINE, OrderStatus.NEEDS_ATTENTION, OrderPaymentStatus.UNPAID))).isFalse();
        assertThat(policy.allowsFulfillmentWork(order(PaymentMethod.ONLINE, OrderStatus.CANCELLED, OrderPaymentStatus.FAILED))).isFalse();
        assertThat(policy.allowsFulfillmentWork(order(PaymentMethod.ONLINE, OrderStatus.CONFIRMED, OrderPaymentStatus.UNPAID))).isFalse();
        assertThat(policy.allowsFulfillmentWork(order(PaymentMethod.ONLINE, OrderStatus.CONFIRMED, OrderPaymentStatus.PAID))).isTrue();
        assertThat(policy.allowsFulfillmentWork(order(PaymentMethod.COD, OrderStatus.CONFIRMED, OrderPaymentStatus.UNPAID))).isTrue();
    }

    @Test
    @DisplayName("Approved delivery policy: national coverage by delivery-enabled Stores, 30.000 once per Order, pickup free")
    void approvedDeliveryPolicyIsTheDefault() {
        CheckoutProperties properties = new CheckoutProperties();
        DeliveryServiceAreaPolicy area = new DeliveryServiceAreaPolicy(properties);
        ShippingFeeCalculator fees = new ShippingFeeCalculator(properties);
        Store store = new Store();
        store.setDeliveryEnabled(true);

        assertThat(area.isDecided()).isTrue();
        assertThat(area.canServe(store, "12 Lê Lợi, Quận 1, TP. Hồ Chí Minh")).isTrue();
        assertThat(area.canServe(store, "5 Hàng Bài, Hoàn Kiếm, Hà Nội")).isTrue(); // the address never matters
        store.setDeliveryEnabled(false);
        assertThat(area.canServe(store, "12 Lê Lợi, Quận 1, TP. Hồ Chí Minh")).isFalse();
        store.setDeliveryEnabled(true);
        store.setStatus(com.oneshop.entity.ActiveStatus.INACTIVE);
        assertThat(area.canServe(store, "12 Lê Lợi, Quận 1, TP. Hồ Chí Minh")).isFalse();

        assertThat(fees.feePerOrder(FulfillmentType.DELIVERY)).hasValueSatisfying(fee ->
                assertThat(fee).isEqualByComparingTo("30000"));
        assertThat(fees.feePerOrder(FulfillmentType.STORE_PICKUP)).hasValueSatisfying(fee ->
                assertThat(fee.signum()).isZero());
        // ONLINE stays closed until BE-05
        assertThat(properties.isOnlineEnabled()).isFalse();
    }

    @Test
    @DisplayName("The UNDECIDED constants remain a fail-closed switch for DELIVERY")
    void killSwitchesCloseDelivery() {
        CheckoutProperties properties = new CheckoutProperties();
        DeliveryServiceAreaPolicy area = new DeliveryServiceAreaPolicy(properties);
        ShippingFeeCalculator fees = new ShippingFeeCalculator(properties);
        Store store = new Store();
        store.setDeliveryEnabled(true);

        properties.setDeliveryServiceAreaPolicy(CheckoutProperties.DeliveryServiceAreaPolicy.UNDECIDED);
        assertThat(area.isDecided()).isFalse();
        assertThat(area.canServe(store, "any address")).isFalse();

        properties.setShippingFeePolicy(CheckoutProperties.ShippingFeePolicy.UNDECIDED);
        assertThat(fees.feePerOrder(FulfillmentType.DELIVERY)).isEmpty();
        assertThat(fees.feePerOrder(FulfillmentType.STORE_PICKUP)).isPresent();
    }

    @Test
    @DisplayName("The delivery fee is configured per Order and cannot be negative")
    void feeAmountIsConfigurable() {
        CheckoutProperties properties = new CheckoutProperties();
        assertThat(properties.getShippingFeePerOrder()).isEqualByComparingTo("30000");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> properties.setShippingFeePerOrder(new java.math.BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Approved timing values cannot be configured below their minimum")
    void propertiesAreValidated() {
        PaymentProperties properties = new PaymentProperties();
        assertThat(properties.getPendingTimeoutMinutes()).isEqualTo(15);
        assertThat(properties.getReconciliationWindowMinutes()).isEqualTo(15);
        assertThat(properties.getTimeoutScanIntervalMs()).isEqualTo(60000);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> properties.setPendingTimeoutMinutes(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("The only time source is the injected server clock")
    void onlyTheInjectedClockIsUsed() {
        assertThat(policy(CREATED).now()).isEqualTo(CREATED);
        assertThat(policy(CREATED.plusHours(2)).now()).isEqualTo(CREATED.plusHours(2));
    }
}
