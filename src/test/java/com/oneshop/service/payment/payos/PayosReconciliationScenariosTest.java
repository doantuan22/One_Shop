package com.oneshop.service.payment.payos;

import com.oneshop.config.PayosProperties;
import com.oneshop.config.PaymentProperties;
import com.oneshop.entity.Order;
import com.oneshop.entity.OrderPaymentStatus;
import com.oneshop.entity.OrderStatus;
import com.oneshop.entity.Payment;
import com.oneshop.entity.PaymentRefund;
import com.oneshop.entity.PaymentRefundComponent;
import com.oneshop.entity.PaymentRefundStatus;
import com.oneshop.entity.PaymentStatus;
import com.oneshop.repository.OrderRepository;
import com.oneshop.repository.PaymentRefundRepository;
import com.oneshop.repository.PaymentRepository;
import com.oneshop.service.PaymentDeadlinePolicy;
import com.oneshop.service.payment.OrderCancellationService;
import com.oneshop.service.payment.OrderStatusWriter;
import com.oneshop.service.payment.PaymentOutcome;
import com.oneshop.service.payment.PaymentReconciliationService;
import com.oneshop.service.payment.PaymentReconciliationService.Trigger;
import com.oneshop.service.payment.RefundService;
import com.oneshop.service.payment.RefundSimulator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static com.oneshop.service.payment.payos.PayosTestSupport.order;
import static com.oneshop.service.payment.payos.PayosTestSupport.paid;
import static com.oneshop.service.payment.payos.PayosTestSupport.payos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The reconciliation engine over the payOS gateway, with the real {@link RefundService}: the money rules of the
 * integration (bank time vs webhook time, late and duplicate money, one cancel/restock, REAL refunds never simulated).
 */
class PayosReconciliationScenariosTest {

    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 10, 27, 10, 0, 0);

    private final OrderRepository orders = mock(OrderRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final PaymentRefundRepository ledger = mock(PaymentRefundRepository.class);
    private final OrderStatusWriter writer = mock(OrderStatusWriter.class);
    private final OrderCancellationService cancellation = mock(OrderCancellationService.class);
    private final RefundSimulator simulator = mock(RefundSimulator.class);
    private final MutableClock clock = new MutableClock(CREATED);
    private final List<PaymentRefund> ledgerRows = new ArrayList<>();
    private PaymentReconciliationService engine;
    private RefundService refunds;
    private Order order;
    private List<Payment> rows;

    @BeforeEach
    void setUp() {
        order = order(5L, CREATED, "200000.00");
        rows = new ArrayList<>();
        when(payments.findForLockedOrder(5L)).thenAnswer(i -> rows);
        when(payments.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(orders.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(orders.findByIdForUpdate(5L)).thenReturn(java.util.Optional.of(order));
        when(ledger.findByPaymentId(anyLong())).thenAnswer(i -> ledgerRows);
        when(ledger.saveAndFlush(any())).thenAnswer(i -> {
            PaymentRefund row = i.getArgument(0);
            if (!ledgerRows.contains(row)) {
                ledgerRows.add(row);
            }
            return row;
        });
        doAnswer(i -> { // the real cancellation closes the Order; the engine must not do it twice
            order.setOrderStatus(OrderStatus.CANCELLED);
            order.setPaymentStatus(i.getArgument(4));
            return null;
        }).when(cancellation).cancelAndRestore(any(), anyString(), anyString(), any(), any());

        doAnswer(i -> { // the status writer moves the Order, as the real one does
            order.setOrderStatus(i.getArgument(1));
            return null;
        }).when(writer).write(any(), any(), any(), anyString());
        ReflectionTestUtils.setField(order.getUser(), "id", 1L);

        PaymentDeadlinePolicy deadlines = new PaymentDeadlinePolicy(new PaymentProperties(), clock);
        RefundService refunds = new RefundService(payments, orders, simulator, ledger);
        this.refunds = refunds;
        engine = new PaymentReconciliationService(orders, payments, new PayosPaymentGateway(new PayosProperties()),
                deadlines, writer, cancellation, refunds);
    }

    private Payment row(long id, long code) {
        Payment row = payos(id, order, code);
        rows.add(row);
        return row;
    }

    private PaymentOutcome reconcile() {
        return engine.reconcileLocked(order, Trigger.PROVIDER_WEBHOOK, null);
    }

    // ------------------------------------------------------------------ confirmation

    @Test
    void paymentRecordedByTheBankBeforeTheDeadlineConfirmsTheOrder() {
        Payment row = paid(row(1, 111), CREATED.plusMinutes(5));
        clock.set(CREATED.plusMinutes(6));

        assertThat(reconcile()).isEqualTo(PaymentOutcome.CONFIRMED);

        assertThat(order.getPaymentStatus()).isEqualTo(OrderPaymentStatus.PAID);
        assertThat(row.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(row.getPaidAt()).isEqualTo(CREATED.plusMinutes(5)); // the bank's time
        verify(writer).write(eq(order), eq(OrderStatus.CONFIRMED), any(), anyString());
        verifyNoInteractions(simulator, cancellation);
    }

    @Test
    void aWebhookThatArrivesAfterTheDeadlineStillConfirmsWhenTheBankRecordedItInTime() {
        Payment row = paid(row(1, 111), CREATED.plusMinutes(14)); // paid at 14:00 min
        clock.set(CREATED.plusMinutes(17));                       // webhook handled at 17:00 min (deadline 15:00)

        assertThat(reconcile()).isEqualTo(PaymentOutcome.CONFIRMED);
        assertThat(row.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    void aBankPaymentAfterTheDeadlineIsNotConfirmedTheOrderIsCancelledAndARealRefundIsRequested() {
        Payment row = paid(row(1, 111), CREATED.plusMinutes(16));
        clock.set(CREATED.plusMinutes(16).plusSeconds(20));

        assertThat(reconcile()).isEqualTo(PaymentOutcome.CANCELLED_LATE_PAYMENT);

        verify(cancellation, times(1)).cancelAndRestore(eq(order), anyString(), anyString(), any(),
                eq(OrderPaymentStatus.PAID));
        assertThat(row.getStatus()).isEqualTo(PaymentStatus.SUCCESS); // the money was collected: kept as evidence
        assertThat(row.getRefundStatus()).isEqualTo(PaymentRefundStatus.MANUAL_REQUESTED);
        assertThat(order.getPaymentStatus()).isEqualTo(OrderPaymentStatus.PAID); // NOT refunded until evidence
        assertThat(ledgerRows).singleElement().satisfies(r -> {
            assertThat(r.getStatus()).isEqualTo(PaymentRefundStatus.MANUAL_REQUESTED);
            assertThat(r.getComponent()).isEqualTo(PaymentRefundComponent.WHOLE_ORDER);
            assertThat(r.getAmount()).isEqualByComparingTo("200000.00");
            assertThat(r.getEvidenceReference()).isNull();
        });
        verifyNoInteractions(simulator);
    }

    // ------------------------------------------------------------------ late money on a cancelled order

    @Test
    void moneyOnACancelledOrderIsRecordedOnceRefundRequestedOnceAndTheOrderStaysCancelled() {
        order.setOrderStatus(OrderStatus.CANCELLED);
        order.setPaymentStatus(OrderPaymentStatus.UNPAID);
        Payment closed = row(1, 111);
        closed.setStatus(PaymentStatus.FAILED); // closed when the order was cancelled
        paid(closed, CREATED.plusMinutes(20));
        clock.set(CREATED.plusMinutes(21));

        assertThat(reconcile()).isEqualTo(PaymentOutcome.LATE_PAYMENT_REFUNDED);
        assertThat(reconcile()).isEqualTo(PaymentOutcome.LATE_PAYMENT_REFUNDED); // a repeated webhook / return / recheck
        assertThat(reconcile()).isEqualTo(PaymentOutcome.LATE_PAYMENT_REFUNDED);

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);     // never revived
        assertThat(closed.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(ledgerRows).hasSize(1);                                        // one refund request
        verify(writer, times(1)).audit(eq(order), any(), anyString());           // and one history entry
        verify(cancellation, never()).cancelAndRestore(any(), anyString(), anyString(), any(), any());
        verifyNoInteractions(simulator);
    }

    @Test
    void anUnderpaymentOnACancelledOrderIsOwedBackForTheAmountReceived() {
        order.setOrderStatus(OrderStatus.CANCELLED);
        order.setPaymentStatus(OrderPaymentStatus.UNPAID);
        Payment partial = row(1, 111);
        partial.setStatus(PaymentStatus.FAILED);
        partial.setProviderStatus("UNDERPAID");
        partial.setProviderAmountPaid(50_000L);
        clock.set(CREATED.plusMinutes(30));

        reconcile();
        reconcile();

        assertThat(partial.getStatus()).isEqualTo(PaymentStatus.FAILED); // never turned into a full payment
        assertThat(ledgerRows).singleElement().satisfies(r -> {
            assertThat(r.getAmount()).isEqualByComparingTo(new BigDecimal("50000"));
            assertThat(r.getStatus()).isEqualTo(PaymentRefundStatus.MANUAL_REQUESTED);
        });
        verifyNoInteractions(simulator);
    }

    @Test
    void aSecondLinkPaidOnAnAlreadyPaidOrderIsRefundedNotCollectedTwice() {
        order.setOrderStatus(OrderStatus.CONFIRMED);
        order.setPaymentStatus(OrderPaymentStatus.PAID);
        Payment first = paid(row(1, 111), CREATED.plusMinutes(2));
        first.setStatus(PaymentStatus.SUCCESS);
        first.setPaidAt(CREATED.plusMinutes(2));
        Payment second = paid(row(2, 222), CREATED.plusMinutes(4));
        second.setStatus(PaymentStatus.FAILED); // its link had been closed when the first one was paid
        clock.set(CREATED.plusMinutes(5));

        assertThat(reconcile()).isEqualTo(PaymentOutcome.ALREADY_PAID);
        assertThat(reconcile()).isEqualTo(PaymentOutcome.ALREADY_PAID);

        assertThat(second.getStatus()).isEqualTo(PaymentStatus.FAILED);   // still exactly one collected payment
        assertThat(first.getRefundStatus()).isEqualTo(PaymentRefundStatus.NONE);
        assertThat(ledgerRows).singleElement().satisfies(r -> assertThat(r.getPayment()).isSameAs(second));
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    // ------------------------------------------------------------------ not paid / unknown

    @Test
    void nothingPaidAtTheDeadlineCancelsAndRestocksExactlyOnce() {
        row(1, 111).setProviderStatus("PENDING");
        clock.set(CREATED.plusMinutes(15).plusSeconds(1));

        assertThat(reconcile()).isEqualTo(PaymentOutcome.CANCELLED_TIMEOUT);
        assertThat(reconcile()).isEqualTo(PaymentOutcome.ALREADY_CANCELLED);
        assertThat(reconcile()).isEqualTo(PaymentOutcome.ALREADY_CANCELLED);

        verify(cancellation, times(1)).cancelAndRestore(any(), anyString(), anyString(), any(), any());
    }

    @Test
    void aClosedLinkBeforeTheDeadlineDoesNotCancelTheOrder() {
        Payment row = row(1, 111);
        row.setProviderStatus("CANCELLED"); // the customer pressed cancel on the payOS page
        row.setProviderAmountPaid(0L);
        clock.set(CREATED.plusMinutes(3));

        assertThat(reconcile()).isEqualTo(PaymentOutcome.AWAITING);
        verifyNoInteractions(cancellation);
    }

    @Test
    void anUnderpaymentWaitsThenNeedsAttentionAndNeverConfirmsOrCancels() {
        Payment row = row(1, 111);
        row.setProviderStatus("UNDERPAID");
        row.setProviderAmountPaid(100_000L);

        clock.set(CREATED.plusMinutes(10));
        assertThat(reconcile()).isEqualTo(PaymentOutcome.AWAITING);
        clock.set(CREATED.plusMinutes(20));
        assertThat(reconcile()).isEqualTo(PaymentOutcome.RECONCILING);
        clock.set(CREATED.plusMinutes(31));
        assertThat(reconcile()).isEqualTo(PaymentOutcome.NEEDS_ATTENTION);

        verify(writer).write(eq(order), eq(OrderStatus.NEEDS_ATTENTION), any(), anyString());
        verifyNoInteractions(cancellation, simulator);
        assertThat(order.getPaymentStatus()).isEqualTo(OrderPaymentStatus.UNPAID);
    }

    @Test
    void theBankTimeMatchesAcrossAServerZoneThatIsNotVietnam() {
        // 16:05 Vietnam time stored as 09:05 server time (UTC); the order was created at 09:00 server time
        LocalDateTime utcCreated = LocalDateTime.of(2026, 10, 27, 9, 0, 0);
        order = order(5L, utcCreated, "200000.00");
        when(orders.findByIdForUpdate(5L)).thenReturn(java.util.Optional.of(order));
        rows.clear();
        Payment row = payos(1, order, 111);
        rows.add(row);
        PayosEvidence evidence = new PayosEvidence(new PayosProperties(), Clock.fixed(Instant.parse("2026-10-27T09:10:00Z"), ZoneOffset.UTC));
        evidence.applyLinkInfo(row, new PayosApi.LinkInfo("l", 111, 200000, 200000, 0, "PAID",
                List.of(new PayosApi.Transaction("FT1", 200000, "2026-10-27 16:05:00"))));
        clock.set(utcCreated.plusMinutes(10));

        assertThat(row.getProviderPaidAt()).isEqualTo(LocalDateTime.of(2026, 10, 27, 9, 5, 0));
        assertThat(reconcile()).isEqualTo(PaymentOutcome.CONFIRMED);
    }

    // ------------------------------------------------------------------ refunds are never simulated for payOS

    @Test
    void theSimulatorRefusesToStandInForARealRefund() {
        Payment row = payos(1, order, 111);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> new com.oneshop.service.payment.DefaultRefundSimulator().simulate(row))
                .isInstanceOf(IllegalStateException.class);
        // a mock payment is still simulated as before
        Payment mock = payos(2, order, 222);
        mock.setProvider(com.oneshop.entity.PaymentProvider.MOCK);
        new com.oneshop.service.payment.DefaultRefundSimulator().simulate(mock);
    }

    @Test
    void requestingTheSameRealRefundTwiceCreatesOneLedgerRow() {
        Payment row = paid(row(1, 111), CREATED.plusMinutes(1));
        row.setStatus(PaymentStatus.SUCCESS);
        row.setPaidAt(CREATED.plusMinutes(1));
        RefundService refunds = new RefundService(payments, orders, simulator, ledger);

        assertThat(refunds.simulate(order, "x")).isEqualTo(RefundService.Result.REFUND_REQUESTED);
        assertThat(refunds.simulate(order, "x")).isEqualTo(RefundService.Result.ALREADY_REQUESTED);

        assertThat(ledgerRows).hasSize(1);
        assertThat(row.getRefundStatus()).isEqualTo(PaymentRefundStatus.MANUAL_REQUESTED);
        assertThat(order.getPaymentStatus()).isNotEqualTo(OrderPaymentStatus.REFUNDED);
        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(payments, org.mockito.Mockito.atLeastOnce()).saveAndFlush(saved.capture());
        verifyNoInteractions(simulator);
    }

    // ------------------------------------------------------------------ cancel racing a payment / simulation is closed

    @Test
    void aCustomerCancelThatRacesAPaidWebhookKeepsTheMoneyAsARefundRequestNotALoss() {
        paid(row(1, 111), CREATED.plusMinutes(5)); // the bank has the money; the order is still PENDING_PAYMENT
        clock.set(CREATED.plusMinutes(6));
        var cancel = new com.oneshop.service.payment.CustomerOrderCancellationService(orders, engine, cancellation, refunds);

        var result = cancel.cancel(order.getUser(), 5L);

        // the payment is applied first (CONFIRMED), then the cancel refunds it: exactly one cancel/restock, one request
        assertThat(result).isEqualTo(com.oneshop.service.payment.CustomerOrderCancellationService.Result.CANCELLED_REFUND_REQUESTED);
        verify(cancellation, times(1)).cancelAndRestore(eq(order), anyString(), anyString(), any(), eq(OrderPaymentStatus.PAID));
        assertThat(ledgerRows).singleElement().satisfies(r -> assertThat(r.getStatus()).isEqualTo(PaymentRefundStatus.MANUAL_REQUESTED));
        assertThat(order.getPaymentStatus()).isEqualTo(OrderPaymentStatus.PAID);
        verifyNoInteractions(simulator);
    }

    @Test
    void aCancelWhileMoneyIsInFlightIsRefusedAndNothingChanges() {
        Payment row = row(1, 111);
        row.setProviderStatus("PROCESSING");
        clock.set(CREATED.plusMinutes(2));
        var cancel = new com.oneshop.service.payment.CustomerOrderCancellationService(orders, engine, cancellation, refunds);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> cancel.cancel(order.getUser(), 5L))
                .isInstanceOf(com.oneshop.exception.PaymentConflictException.class);
        verifyNoInteractions(cancellation);
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void aCancelWithNothingPaidCancelsOnceAndRefundsNothing() {
        row(1, 111).setProviderStatus("PENDING");
        clock.set(CREATED.plusMinutes(2));
        var cancel = new com.oneshop.service.payment.CustomerOrderCancellationService(orders, engine, cancellation, refunds);

        assertThat(cancel.cancel(order.getUser(), 5L))
                .isEqualTo(com.oneshop.service.payment.CustomerOrderCancellationService.Result.CANCELLED);
        assertThat(cancel.cancel(order.getUser(), 5L))
                .isEqualTo(com.oneshop.service.payment.CustomerOrderCancellationService.Result.ALREADY_CANCELLED);

        verify(cancellation, times(1)).cancelAndRestore(any(), anyString(), anyString(), any(), eq(OrderPaymentStatus.UNPAID));
        assertThat(ledgerRows).isEmpty();
    }

    @Test
    void simulationIsRefusedWithRealMoneyAndNeverProducesASuccess() {
        row(1, 111);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> engine.simulate("alice@test", 5L, 1L,
                        PaymentReconciliationService.SimulatedResult.SUCCESS))
                .isInstanceOf(com.oneshop.exception.PaymentConflictException.class);
        assertThat(order.getPaymentStatus()).isEqualTo(OrderPaymentStatus.UNPAID);
        assertThat(rows.get(0).getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    /** A clock the test can move. */
    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(LocalDateTime start) {
            this.now = start.toInstant(ZoneOffset.UTC);
        }

        void set(LocalDateTime time) {
            this.now = time.toInstant(ZoneOffset.UTC);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
