package com.oneshop.service.payment.payos;

import com.oneshop.config.PayosProperties;
import com.oneshop.config.PaymentProperties;
import com.oneshop.entity.Order;
import com.oneshop.entity.Payment;
import com.oneshop.entity.PaymentStatus;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.PaymentConflictException;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.repository.OrderRepository;
import com.oneshop.repository.PaymentRepository;
import com.oneshop.service.PaymentDeadlinePolicy;
import com.oneshop.service.payment.PaymentOutcome;
import com.oneshop.service.payment.PaymentReconciliationService;
import com.oneshop.service.payment.PaymentReconciliationService.Trigger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.oneshop.service.payment.payos.PayosTestSupport.EMAIL;
import static com.oneshop.service.payment.payos.PayosTestSupport.order;
import static com.oneshop.service.payment.payos.PayosTestSupport.paid;
import static com.oneshop.service.payment.payos.PayosTestSupport.payos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The hosted-checkout flow against an in-memory payment table and a mocked payOS. */
class PayosPaymentFlowTest {

    private static final Instant NOW = Instant.parse("2026-10-27T10:01:00Z");
    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 10, 27, 10, 0, 0); // deadline 10:15

    private final PayosApi api = mock(PayosApi.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final PaymentReconciliationService reconciliation = mock(PaymentReconciliationService.class);
    private final List<Payment> table = new ArrayList<>();
    private final PayosProperties properties = new PayosProperties();
    private PayosPaymentFlow flow;
    private Order order;

    @BeforeEach
    void setUp() {
        properties.setClientId("id");
        properties.setApiKey("key");
        properties.setChecksumKey("sum");
        properties.setPublicBaseUrl("https://shop.example.com");
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        order = order(7L, CREATED, "200000.00");
        when(orders.findByIdForUpdate(7L)).thenAnswer(i -> Optional.of(order));
        when(payments.findForLockedOrder(7L)).thenAnswer(i -> new ArrayList<>(table));
        when(payments.findByOrderIdOrderByIdAsc(7L)).thenAnswer(i -> new ArrayList<>(table));
        when(payments.findOrderIdById(anyLong())).thenReturn(Optional.of(7L));
        when(payments.existsByProviderOrderCode(anyLong())).thenAnswer(i -> table.stream()
                .anyMatch(p -> i.getArgument(0).equals(p.getProviderOrderCode())));
        when(payments.findById(anyLong())).thenAnswer(i -> table.stream()
                .filter(p -> i.getArgument(0).equals(p.getId())).findFirst());
        when(payments.saveAndFlush(any())).thenAnswer(i -> {
            Payment p = i.getArgument(0);
            if (p.getId() == null) {
                ReflectionTestUtils.setField(p, "id", 100L + table.size());
            }
            if (!table.contains(p)) {
                table.add(p);
            }
            return p;
        });
        PaymentDeadlinePolicy deadlines = new PaymentDeadlinePolicy(new PaymentProperties(), clock);
        flow = new PayosPaymentFlow(api, properties, new PayosEvidence(properties, clock), payments, orders, deadlines,
                new PayosPaymentGateway(properties), reconciliation, clock, new PayosLinkCanceller(api),
                mock(PlatformTransactionManager.class));
    }

    private static PayosApi.LinkCreated created(String id) {
        return new PayosApi.LinkCreated(id, "https://pay.payos.vn/web/" + id, 0, 200000, "PENDING");
    }

    // ------------------------------------------------------------------ create and reuse

    @Test
    void createsOneLinkForTheDatabaseAmountWithTheOrderDeadlineAndOurUrls() {
        when(api.createLink(any())).thenReturn(created("link-1"));

        String url = flow.start(EMAIL, 7L).checkoutUrl();

        assertThat(url).isEqualTo("https://pay.payos.vn/web/link-1");
        ArgumentCaptor<PayosApi.LinkRequest> sent = ArgumentCaptor.forClass(PayosApi.LinkRequest.class);
        verify(api).createLink(sent.capture());
        PayosApi.LinkRequest request = sent.getValue();
        assertThat(request.amount()).isEqualTo(200_000L);
        assertThat(request.orderCode()).isBetween(1L, PayosMath.MAX_ORDER_CODE);
        assertThat(request.returnUrl()).isEqualTo("https://shop.example.com/orders/7/payments/payos/return");
        assertThat(request.cancelUrl()).isEqualTo("https://shop.example.com/orders/7/payments/payos/cancel");
        assertThat(request.description()).hasSizeLessThanOrEqualTo(25);
        // the link dies with the payment window: 10:15, never later
        assertThat(request.expiredAtEpochSeconds())
                .isEqualTo(CREATED.plusMinutes(15).toEpochSecond(ZoneOffset.UTC));
        Payment row = table.get(0);
        assertThat(row.getProviderOrderCode()).isEqualTo(request.orderCode());
        assertThat(row.getProviderLinkId()).isEqualTo("link-1");
        assertThat(row.getCheckoutUrl()).isEqualTo(url);
        assertThat(row.getAmount()).isEqualByComparingTo("200000.00");
        assertThat(row.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void repeatedClicksReuseTheSameLinkWithoutTouchingPayosOrExtendingTheDeadline() {
        when(api.createLink(any())).thenReturn(created("link-1"));

        String first = flow.start(EMAIL, 7L).checkoutUrl();
        String second = flow.start(EMAIL, 7L).checkoutUrl();
        String third = flow.start(EMAIL, 7L).checkoutUrl();

        assertThat(second).isEqualTo(first);
        assertThat(third).isEqualTo(first);
        verify(api, times(1)).createLink(any());
        assertThat(table).hasSize(1);
    }

    // ------------------------------------------------------------------ crash and network failures

    @Test
    void whenPayosCreatedTheLinkButTheAnswerWasLostTheExistingLinkIsAdoptedWithTheSameOrderCode() {
        when(api.createLink(any())).thenThrow(new PayosException.Unavailable("timeout", null));
        when(api.getLink(anyLong())).thenReturn(new PayosApi.LinkInfo("link-9", 0, 200000, 0, 200000, "PENDING", List.of()));
        // getLink answers for whatever code was stored: patch the orderCode in after the row exists
        when(api.getLink(anyLong())).thenAnswer(i -> new PayosApi.LinkInfo("link-9", i.getArgument(0), 200000, 0, 200000,
                "PENDING", List.of()));

        String url = flow.start(EMAIL, 7L).checkoutUrl();

        assertThat(url).isEqualTo("https://pay.payos.vn/web/link-9");
        assertThat(table).hasSize(1);
        assertThat(table.get(0).getProviderLinkId()).isEqualTo("link-9");
        ArgumentCaptor<Long> looked = ArgumentCaptor.forClass(Long.class);
        verify(api).getLink(looked.capture());
        assertThat(looked.getValue()).isEqualTo(table.get(0).getProviderOrderCode()); // looked up by OUR code
    }

    @Test
    void anApplicationCrashBeforeTheLinkWasSavedIsRecoveredByResendingTheSameOrderCode() {
        when(api.createLink(any())).thenThrow(new PayosException.Unavailable("boom", null));
        when(api.getLink(anyLong())).thenThrow(new PayosException.NotFound("none", null));

        assertThatThrownBy(() -> flow.start(EMAIL, 7L)).isInstanceOf(PaymentConflictException.class)
                .extracting("code").isEqualTo(PaymentConflictException.GATEWAY_UNAVAILABLE);
        assertThat(table).hasSize(1);
        long code = table.get(0).getProviderOrderCode();
        assertThat(table.get(0).getProviderLinkId()).isNull();

        // the customer presses again after the outage: the SAME attempt and orderCode are used
        org.mockito.Mockito.reset(api);
        when(api.createLink(any())).thenReturn(created("link-2"));
        String url = flow.start(EMAIL, 7L).checkoutUrl();

        assertThat(url).isEqualTo("https://pay.payos.vn/web/link-2");
        assertThat(table).hasSize(1);
        ArgumentCaptor<PayosApi.LinkRequest> sent = ArgumentCaptor.forClass(PayosApi.LinkRequest.class);
        verify(api).createLink(sent.capture());
        assertThat(sent.getValue().orderCode()).isEqualTo(code);
    }

    @Test
    void aDuplicateRefusalAdoptsTheExistingLinkInsteadOfCreatingAnotherRequest() {
        when(api.createLink(any())).thenThrow(new PayosException.Rejected("order code exists", null));
        when(api.getLink(anyLong())).thenAnswer(i -> new PayosApi.LinkInfo("link-5", i.getArgument(0), 200000, 0, 200000,
                "PENDING", List.of()));

        assertThat(flow.start(EMAIL, 7L).checkoutUrl()).isEqualTo("https://pay.payos.vn/web/link-5");
        assertThat(table).hasSize(1);
    }

    @Test
    void aDefiniteRefusalWithNoExistingLinkClosesTheEmptyAttemptAndReportsClearly() {
        when(api.createLink(any())).thenThrow(new PayosException.Rejected("bad", null));
        when(api.getLink(anyLong())).thenThrow(new PayosException.NotFound("none", null));

        assertThatThrownBy(() -> flow.start(EMAIL, 7L)).isInstanceOf(PaymentConflictException.class);
        assertThat(table.get(0).getStatus()).isEqualTo(PaymentStatus.FAILED);
        verifyNoInteractions(reconciliation);
    }

    // ------------------------------------------------------------------ guards

    @Test
    void aFractionalOrOutOfRangeAmountIsRefusedBeforeAnyPayosCall() {
        order.setTotalAmount(new java.math.BigDecimal("200000.50"));
        assertThatThrownBy(() -> flow.start(EMAIL, 7L)).isInstanceOf(BadRequestException.class);
        order.setTotalAmount(new java.math.BigDecimal("999"));
        assertThatThrownBy(() -> flow.start(EMAIL, 7L)).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(api);
        assertThat(table).isEmpty();
    }

    @Test
    void anotherCustomersOrderIsNotFound() {
        assertThatThrownBy(() -> flow.start("mallory@test", 7L)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(api);
    }

    @Test
    void anExpiredOrderGetsNoLink() {
        order = order(7L, CREATED.minusMinutes(30), "200000.00");
        assertThatThrownBy(() -> flow.start(EMAIL, 7L)).isInstanceOf(PaymentConflictException.class)
                .extracting("code").isEqualTo(PaymentConflictException.PAYMENT_NOT_PAYABLE);
        verifyNoInteractions(api);
    }

    @Test
    void anUnconfiguredServerCreatesNoLink() {
        properties.setApiKey("");
        assertThatThrownBy(() -> flow.start(EMAIL, 7L)).isInstanceOf(PaymentConflictException.class)
                .extracting("code").isEqualTo(PaymentConflictException.GATEWAY_DISABLED);
        verifyNoInteractions(api);
    }

    @Test
    void moneyAlreadyAtTheBankIsAppliedNotPaidTwice() {
        table.add(paid(payos(100, order, 555L), CREATED.plusMinutes(2)));

        assertThat(flow.start(EMAIL, 7L).checkoutUrl()).isNull();

        verify(reconciliation).reconcileLocked(eq(order), eq(Trigger.CUSTOMER_CALLBACK), any());
        verify(api, never()).createLink(any());
    }

    @Test
    void moneyInFlightBlocksANewLink() {
        Payment row = payos(100, order, 555L);
        row.setProviderStatus("PROCESSING");
        table.add(row);
        assertThatThrownBy(() -> flow.start(EMAIL, 7L)).isInstanceOf(PaymentConflictException.class)
                .extracting("code").isEqualTo(PaymentConflictException.PAYMENT_RECONCILING);
        verify(api, never()).createLink(any());
    }

    @Test
    void aLinkPayosClosedIsReplacedByAFreshAttemptWithANewOrderCode() {
        Payment old = payos(100, order, 555L);
        old.setProviderLinkId("old");
        old.setCheckoutUrl("https://pay.payos.vn/web/old");
        old.setProviderStatus("CANCELLED");
        table.add(old);
        when(api.createLink(any())).thenReturn(created("link-new"));

        assertThat(flow.start(EMAIL, 7L).checkoutUrl()).isEqualTo("https://pay.payos.vn/web/link-new");

        assertThat(old.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(table).hasSize(2);
        assertThat(table.get(1).getProviderOrderCode()).isNotEqualTo(555L);
        assertThat(table.stream().filter(p -> p.getStatus() == PaymentStatus.PENDING)).hasSize(1);
    }

    // ------------------------------------------------------------------ refresh / sync

    @Test
    void refreshStoresPayosAnswersAsEvidence() {
        Payment row = payos(100, order, 555L);
        table.add(row);
        when(api.getLink(555L)).thenReturn(new PayosApi.LinkInfo("l", 555L, 200000, 200000, 0, "PAID",
                List.of(new PayosApi.Transaction("FT1", 200000, "2026-10-27 17:00:30"))));

        assertThat(flow.refresh(7L)).isTrue();

        assertThat(row.getProviderStatus()).isEqualTo("PAID");
        assertThat(row.getProviderPaidAt()).isEqualTo(LocalDateTime.of(2026, 10, 27, 10, 0, 30));
    }

    @Test
    void whenPayosCannotAnswerNothingIsDecidedFromStaleEvidence() {
        table.add(payos(100, order, 555L));
        when(api.getLink(555L)).thenThrow(new PayosException.Unavailable("down", null));

        assertThat(flow.refresh(7L)).isFalse();
        assertThat(flow.sync(7L, Trigger.TIMEOUT_SCAN)).isEqualTo(PaymentOutcome.RECONCILING);
        verify(reconciliation, never()).reconcile(anyLong(), any());
    }

    @Test
    void aLinkPayosNeverSawDoesNotBlockTheDecision() {
        table.add(payos(100, order, 555L)); // the process died before calling payOS
        when(api.getLink(555L)).thenThrow(new PayosException.NotFound("none", null));
        when(reconciliation.reconcile(7L, Trigger.CUSTOMER_RETURN)).thenReturn(PaymentOutcome.AWAITING);

        assertThat(flow.sync(7L, Trigger.CUSTOMER_RETURN)).isEqualTo(PaymentOutcome.AWAITING);
    }

    @Test
    void closingALinkAfterCancellationIsQuietEvenWhenPayosRefuses() {
        Payment row = payos(100, order, 555L);
        when(api.cancelLink(anyLong(), anyString())).thenThrow(new PayosException.Rejected("already paid", null));

        new PayosLinkCanceller(api).cancelAfterCommit(row); // outside a transaction: runs at once, must not throw

        verify(api).cancelLink(eq(555L), anyString());
    }
}
