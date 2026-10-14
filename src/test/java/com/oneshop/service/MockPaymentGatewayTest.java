package com.oneshop.service;

import com.oneshop.config.PaymentProperties;
import com.oneshop.entity.Order;
import com.oneshop.entity.OrderStatus;
import com.oneshop.entity.OrderStatusHistory;
import com.oneshop.entity.Payment;
import com.oneshop.entity.PaymentMethod;
import com.oneshop.entity.PaymentStatus;
import com.oneshop.exception.BadRequestException;
import com.oneshop.repository.OrderRepository;
import com.oneshop.repository.OrderStatusHistoryRepository;
import com.oneshop.service.payment.GatewayVerification;
import com.oneshop.service.payment.LedgerState;
import com.oneshop.service.payment.MockLedger;
import com.oneshop.service.payment.MockPaymentGateway;
import com.oneshop.service.payment.OrderStatusWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Pure tests of the durable mock ledger encoding, the gateway answers and the payment-domain Order edges. */
class MockPaymentGatewayTest {

    private final PaymentProperties properties = new PaymentProperties();
    private final MockPaymentGateway gateway = new MockPaymentGateway(properties);
    private final LocalDateTime t0 = LocalDateTime.of(2026, 10, 14, 10, 0, 0);

    private Order gatewayOrder() {
        Order o=new Order();org.springframework.test.util.ReflectionTestUtils.setField(o,"id",100L);o.setTotalAmount(new java.math.BigDecimal("159000"));return o;
    }

    private Payment attempt() {
        Payment p = new Payment();
        p.setOrder(gatewayOrder());p.setAmount(gatewayOrder().getTotalAmount());
        p.setMethod(PaymentMethod.ONLINE);
        p.setStatus(PaymentStatus.PENDING);
        p.setTransactionCode(MockLedger.newReference());
        return p;
    }

    @Test
    @DisplayName("a new attempt is AWAITING with a backend MOCK reference")
    void newAttemptAwaiting() {
        Payment p = attempt();

        assertThat(p.getTransactionCode()).startsWith("MOCK-");
        assertThat(MockLedger.stateOf(p)).isEqualTo(LedgerState.AWAITING);
        assertThat(gateway.verify(gatewayOrder(), List.of(p)).result()).isEqualTo(GatewayVerification.Result.NOT_PAID);
    }

    @Test
    @DisplayName("in doubt is a marker on the reference; the reference itself never changes")
    void inDoubtKeepsReference() {
        Payment p = attempt();
        String reference = p.getTransactionCode();

        gateway.markInDoubt(p, t0);

        assertThat(MockLedger.stateOf(p)).isEqualTo(LedgerState.IN_DOUBT);
        assertThat(p.getTransactionCode()).isEqualTo(reference + "~UNKNOWN");
        assertThat(MockLedger.reference(p.getTransactionCode())).isEqualTo(reference);
        assertThat(p.getPaidAt()).isEqualTo(t0);
        GatewayVerification v = gateway.verify(gatewayOrder(), List.of(p));
        assertThat(v.result()).isEqualTo(GatewayVerification.Result.UNKNOWN);
        assertThat(v.reference()).isEqualTo(reference);
        assertThat(v.conclusive()).isFalse();
    }

    @Test
    @DisplayName("an in-doubt attempt settles to collected (at the submission time) or to declined")
    void settleInDoubt() {
        Payment collected = attempt();
        gateway.markInDoubt(collected, t0);
        gateway.settleInDoubt(collected, true);
        assertThat(MockLedger.stateOf(collected)).isEqualTo(LedgerState.CAPTURED);
        GatewayVerification v = gateway.verify(gatewayOrder(), List.of(collected));
        assertThat(v.result()).isEqualTo(GatewayVerification.Result.SUCCESS);
        assertThat(v.capturedAt()).isEqualTo(t0);

        Payment declined = attempt();
        gateway.markInDoubt(declined, t0);
        gateway.settleInDoubt(declined, false);
        assertThat(declined.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(declined.getPaidAt()).isNull();
        assertThat(gateway.verify(gatewayOrder(), List.of(declined)).result()).isEqualTo(GatewayVerification.Result.FAILED);
    }

    @Test
    @DisplayName("capture and decline apply only to an attempt still waiting for the gateway")
    void transitionsAreGuarded() {
        Payment p = attempt();
        gateway.capture(p, t0.plusSeconds(5));
        assertThat(MockLedger.stateOf(p)).isEqualTo(LedgerState.CAPTURED);
        assertThatThrownBy(() -> gateway.decline(p)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> gateway.markInDoubt(p, t0)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> gateway.settleInDoubt(p, true)).isInstanceOf(BadRequestException.class);

        p.setStatus(PaymentStatus.SUCCESS); // applied
        assertThat(MockLedger.stateOf(p)).isEqualTo(LedgerState.SETTLED);
        assertThatThrownBy(() -> gateway.capture(p, t0)).isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("a collection wins over a doubt; the earliest collection is the one reported")
    void precedence() {
        Payment doubtful = attempt();
        gateway.markInDoubt(doubtful, t0);
        Payment late = attempt();
        gateway.capture(late, t0.plusMinutes(20));
        Payment early = attempt();
        gateway.capture(early, t0.plusMinutes(2));

        GatewayVerification v = gateway.verify(gatewayOrder(), List.of(doubtful, late, early));

        assertThat(v.result()).isEqualTo(GatewayVerification.Result.SUCCESS);
        assertThat(v.capturedAt()).isEqualTo(t0.plusMinutes(2));
    }

    @Test
    @DisplayName("offline payments are invisible to the gateway")
    void onlyOnlineRowsCount() {
        Payment cod = new Payment();
        cod.setMethod(PaymentMethod.COD);
        cod.setStatus(PaymentStatus.SUCCESS);
        cod.setPaidAt(t0);
        cod.setTransactionCode("PAY-1");

        assertThat(gateway.verify(gatewayOrder(), List.of(cod)).result()).isEqualTo(GatewayVerification.Result.NOT_PAID);
    }

    @Test
    @DisplayName("the gateway switch follows the property (off in production)")
    void enabledFlag() {
        assertThat(gateway.enabled()).isTrue();
        properties.setMockGatewayEnabled(false);
        assertThat(gateway.enabled()).isFalse();
    }

    // ---------------------------------------------------------------- the payment-domain Order edges

    @Test
    @DisplayName("only the allowed payment-domain edges are written, with history; CANCELLED is never left")
    void statusWriterEdges() {
        OrderRepository orders = mock(OrderRepository.class);
        OrderStatusHistoryRepository histories = mock(OrderStatusHistoryRepository.class);
        OrderStatusWriter writer = new OrderStatusWriter(orders, histories);

        for (OrderStatus[] edge : new OrderStatus[][]{
                {OrderStatus.PENDING_PAYMENT, OrderStatus.CONFIRMED},
                {OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED},
                {OrderStatus.PENDING_PAYMENT, OrderStatus.NEEDS_ATTENTION},
                {OrderStatus.NEEDS_ATTENTION, OrderStatus.CONFIRMED},
                {OrderStatus.NEEDS_ATTENTION, OrderStatus.CANCELLED},
                {OrderStatus.CONFIRMED, OrderStatus.CANCELLED}}) {
            Order order = new Order();
            order.setOrderStatus(edge[0]);
            writer.write(order, edge[1], null, "ghi chú");
            assertThat(order.getOrderStatus()).isEqualTo(edge[1]);
        }
        verify(histories, times(6)).save(any(OrderStatusHistory.class));

        for (OrderStatus[] edge : new OrderStatus[][]{
                {OrderStatus.CANCELLED, OrderStatus.CONFIRMED},
                {OrderStatus.CANCELLED, OrderStatus.PENDING_PAYMENT},
                {OrderStatus.CONFIRMED, OrderStatus.PENDING_PAYMENT},
                {OrderStatus.CONFIRMED, OrderStatus.NEEDS_ATTENTION},
                {OrderStatus.PROCESSING, OrderStatus.CANCELLED},
                {OrderStatus.COMPLETED, OrderStatus.CANCELLED},
                {OrderStatus.NEEDS_ATTENTION, OrderStatus.PENDING_PAYMENT}}) {
            Order order = new Order();
            order.setOrderStatus(edge[0]);
            assertThatThrownBy(() -> writer.write(order, edge[1], null, "x")).isInstanceOf(BadRequestException.class);
            assertThat(order.getOrderStatus()).isEqualTo(edge[0]);
        }
        verify(histories, times(6)).save(any(OrderStatusHistory.class));
    }

    @Test
    @DisplayName("an audit note is abbreviated to the 500 characters of the schema, never an error")
    void auditNoteIsAbbreviated() {
        OrderRepository orders = mock(OrderRepository.class);
        OrderStatusHistoryRepository histories = mock(OrderStatusHistoryRepository.class);
        OrderStatusWriter writer = new OrderStatusWriter(orders, histories);
        Order order = new Order();
        order.setOrderStatus(OrderStatus.NEEDS_ATTENTION);

        writer.audit(order, null, "x".repeat(900));

        ArgumentCaptor<OrderStatusHistory> captor = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(histories).save(captor.capture());
        assertThat(captor.getValue().getNote()).hasSize(500);
        assertThat(captor.getValue().getOldStatus()).isEqualTo(OrderStatus.NEEDS_ATTENTION);
        assertThat(captor.getValue().getNewStatus()).isEqualTo(OrderStatus.NEEDS_ATTENTION);
        verify(orders, never()).saveAndFlush(any());
    }
}
