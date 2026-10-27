package com.oneshop.service.payment.payos;

import com.oneshop.config.PayosProperties;
import com.oneshop.entity.Order;
import com.oneshop.entity.Payment;
import com.oneshop.entity.PaymentMethod;
import com.oneshop.entity.PaymentProvider;
import com.oneshop.entity.PaymentStatus;
import com.oneshop.service.payment.GatewayVerification;
import com.oneshop.service.payment.GatewayVerification.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static com.oneshop.service.payment.payos.PayosTestSupport.order;
import static com.oneshop.service.payment.payos.PayosTestSupport.paid;
import static com.oneshop.service.payment.payos.PayosTestSupport.payos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayosPaymentGatewayTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 27, 10, 0, 0);

    private final PayosProperties properties = new PayosProperties();
    private PayosPaymentGateway gateway;
    private Order order;

    @BeforeEach
    void setUp() {
        gateway = new PayosPaymentGateway(properties);
        order = order(7L, T0, "200000.00");
    }

    @Test
    void simulationIsNeverAvailableWithRealMoney() {
        assertThat(gateway.enabled()).isFalse();
        assertThat(gateway.provider()).isEqualTo(PaymentProvider.PAYOS);
        Payment row = payos(1, order, 111);
        assertThatThrownBy(() -> gateway.capture(row, T0)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> gateway.decline(row)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> gateway.markInDoubt(row, T0)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> gateway.settleInDoubt(row, true)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void onlineIsAvailableOnlyWhenPayosIsConfigured() {
        assertThat(gateway.onlineAvailable()).isFalse();
        properties.setClientId("id");
        properties.setApiKey("key");
        properties.setChecksumKey("sum");
        assertThat(gateway.onlineAvailable()).isFalse(); // still no public https URL
        properties.setPublicBaseUrl("https://shop.example.com");
        assertThat(gateway.onlineAvailable()).isTrue();
        properties.setPublicBaseUrl("http://shop.example.com");
        assertThat(gateway.onlineAvailable()).isFalse(); // plain http is refused for a public host
    }

    @Test
    void noEvidenceMeansNotPaidNeverFailed() {
        assertThat(gateway.verify(order, List.of()).result()).isEqualTo(Result.NOT_PAID);
        Payment pending = payos(1, order, 111);
        pending.setProviderStatus("PENDING");
        assertThat(gateway.verify(order, List.of(pending)).result()).isEqualTo(Result.NOT_PAID);
    }

    @Test
    void closedLinksAreNotPaidAndNeverCancelTheOrderAsFailed() {
        for (String status : List.of("CANCELLED", "EXPIRED", "FAILED")) {
            Payment row = payos(1, order, 111);
            row.setProviderStatus(status);
            row.setProviderAmountPaid(0L);
            assertThat(gateway.verify(order, List.of(row)).result()).as(status).isEqualTo(Result.NOT_PAID);
        }
    }

    @Test
    void fullPaymentIsSuccessAtTheBankTimeAndNamesTheRow() {
        LocalDateTime bank = T0.plusMinutes(5);
        Payment row = paid(payos(1, order, 111), bank);
        GatewayVerification v = gateway.verify(order, List.of(row));
        assertThat(v.result()).isEqualTo(Result.SUCCESS);
        assertThat(v.capturedAt()).isEqualTo(bank);
        assertThat(v.paymentId()).isEqualTo(1L);
    }

    @Test
    void underpaymentIsUnknownNotSuccess() {
        Payment row = payos(1, order, 111);
        row.setProviderStatus("UNDERPAID");
        row.setProviderAmountPaid(50_000L);
        assertThat(gateway.verify(order, List.of(row)).result()).isEqualTo(Result.UNKNOWN);
    }

    @Test
    void overpaymentAndProcessingAreUnknown() {
        Payment over = paid(payos(1, order, 111), T0.plusMinutes(1));
        over.setProviderAmountPaid(250_000L);
        assertThat(gateway.verify(order, List.of(over)).result()).isEqualTo(Result.UNKNOWN);

        Payment processing = payos(2, order, 222);
        processing.setProviderStatus("PROCESSING");
        assertThat(gateway.verify(order, List.of(processing)).result()).isEqualTo(Result.UNKNOWN);
    }

    @Test
    void paidWithoutABankTimeIsUnknown() {
        Payment row = paid(payos(1, order, 111), null);
        assertThat(gateway.verify(order, List.of(row)).result()).isEqualTo(Result.UNKNOWN);
    }

    @Test
    void aStoredAmountThatDiffersFromTheOrderTotalIsUnknown() {
        Payment row = paid(payos(1, order, 111), T0.plusMinutes(1));
        row.setAmount(new java.math.BigDecimal("100000.00"));
        assertThat(gateway.verify(order, List.of(row)).result()).isEqualTo(Result.UNKNOWN);
    }

    @Test
    void earliestFullPaymentWinsWhenTwoLinksWerePaid() {
        Payment later = paid(payos(2, order, 222), T0.plusMinutes(9));
        Payment earlier = paid(payos(1, order, 111), T0.plusMinutes(3));
        GatewayVerification v = gateway.verify(order, List.of(later, earlier));
        assertThat(v.paymentId()).isEqualTo(1L);
    }

    @Test
    void mockRowsAreIgnored() {
        Payment mock = payos(1, order, 111);
        mock.setProvider(PaymentProvider.MOCK);
        mock.setStatus(PaymentStatus.SUCCESS);
        mock.setPaidAt(T0);
        mock.setMethod(PaymentMethod.ONLINE);
        assertThat(gateway.verify(order, List.of(mock)).result()).isEqualTo(Result.NOT_PAID);
    }

    @Test
    void applyCollectedSetsSuccessAtTheGivenTimeWithoutTouchingTheReference() {
        Payment row = payos(1, order, 111);
        gateway.applyCollected(row, T0.plusMinutes(2));
        assertThat(row.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(row.getPaidAt()).isEqualTo(T0.plusMinutes(2));
        assertThat(row.getTransactionCode()).isEqualTo("PAYOS-111");
    }
}
