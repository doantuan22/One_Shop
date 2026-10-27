package com.oneshop.service.payment.payos;

import com.oneshop.config.PayosProperties;
import com.oneshop.entity.Order;
import com.oneshop.entity.Payment;
import com.oneshop.entity.PaymentProviderEvent;
import com.oneshop.repository.OrderRepository;
import com.oneshop.repository.PaymentProviderEventRepository;
import com.oneshop.repository.PaymentRepository;
import com.oneshop.service.payment.PaymentReconciliationService;
import com.oneshop.service.payment.PaymentReconciliationService.Trigger;
import com.oneshop.service.payment.payos.PayosWebhookService.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.oneshop.service.payment.payos.PayosTestSupport.order;
import static com.oneshop.service.payment.payos.PayosTestSupport.payos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PayosWebhookServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-27T10:05:00Z"), ZoneOffset.UTC);

    private final PayosApi api = mock(PayosApi.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final PaymentProviderEventRepository events = mock(PaymentProviderEventRepository.class);
    private final PaymentReconciliationService reconciliation = mock(PaymentReconciliationService.class);
    private final PayosPaymentFlow flow = mock(PayosPaymentFlow.class);
    private final List<PaymentProviderEvent> journal = new ArrayList<>();
    private PayosWebhookService service;
    private Order order;
    private Payment row;

    @BeforeEach
    void setUp() {
        PayosProperties properties = new PayosProperties();
        order = order(7L, LocalDateTime.of(2026, 10, 27, 10, 0, 0), "200000.00");
        row = payos(100, order, 555L);
        row.setProviderLinkId("link-abc");
        when(orders.findByIdForUpdate(7L)).thenReturn(Optional.of(order));
        when(payments.findOrderIdByProviderOrderCode(555L)).thenReturn(Optional.of(7L));
        when(payments.findByProviderOrderCode(555L)).thenReturn(Optional.of(row));
        when(payments.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(events.findByProviderAndEventKey(any(), anyString())).thenAnswer(i -> journal.stream()
                .filter(e -> e.getEventKey().equals(i.getArgument(1))).findFirst());
        when(events.saveAndFlush(any())).thenAnswer(i -> {
            journal.add(i.getArgument(0));
            return i.getArgument(0);
        });
        service = new PayosWebhookService(api, new PayosEvidence(properties, CLOCK), payments, orders, events,
                reconciliation, flow, CLOCK, properties, mock(PlatformTransactionManager.class));
    }

    private static PayosApi.WebhookEvent event(long orderCode, long amount, String link, String ref) {
        return new PayosApi.WebhookEvent(true, "00", orderCode, amount, "VND", link, ref, "2026-10-27 17:03:00", "00");
    }

    @Test
    void aBadSignatureIsRejectedAndNothingIsTouched() {
        when(api.verifyWebhook("raw")).thenThrow(new PayosException.InvalidSignature("bad", null));

        assertThat(service.handle("raw")).isEqualTo(Result.INVALID_SIGNATURE);

        verify(payments, never()).findOrderIdByProviderOrderCode(any());
        verify(reconciliation, never()).reconcileLocked(any(), any(), any());
        assertThat(journal).isEmpty();
    }

    @Test
    void payosSampleForAnUnknownOrderCodeIsAcknowledgedAndPaysNothing() {
        // what payOS sends when the webhook URL is confirmed: orderCode 123, 3000 VND, validly signed
        when(api.verifyWebhook("raw")).thenReturn(event(123L, 3000, "124c3329", "TF230204212323"));
        when(payments.findOrderIdByProviderOrderCode(123L)).thenReturn(Optional.empty());

        assertThat(service.handle("raw")).isEqualTo(Result.ACCEPTED);

        assertThat(journal).singleElement().satisfies(e ->
                assertThat(e.getOutcome()).isEqualTo(PaymentProviderEvent.Outcome.UNKNOWN_ORDER));
        verify(reconciliation, never()).reconcileLocked(any(), any(), any());
        assertThat(row.getProviderStatus()).isNull();
    }

    @Test
    void aVerifiedFullPaymentIsStoredAsEvidenceAndHandedToTheEngineUnderTheOrderLock() {
        when(api.verifyWebhook("raw")).thenReturn(event(555L, 200000, "link-abc", "FT1"));

        assertThat(service.handle("raw")).isEqualTo(Result.ACCEPTED);

        assertThat(row.getProviderStatus()).isEqualTo("PAID");
        assertThat(row.getProviderPaidAt()).isEqualTo(LocalDateTime.of(2026, 10, 27, 10, 3, 0)); // bank time, UTC
        assertThat(journal).singleElement().satisfies(e -> {
            assertThat(e.getOutcome()).isEqualTo(PaymentProviderEvent.Outcome.APPLIED);
            assertThat(e.getPaymentId()).isEqualTo(100L);
        });
        verify(reconciliation).reconcileLocked(order, Trigger.PROVIDER_WEBHOOK, null);
    }

    @Test
    void aRepeatedDeliveryIsHarmless() {
        when(api.verifyWebhook("raw")).thenReturn(event(555L, 200000, "link-abc", "FT1"));

        assertThat(service.handle("raw")).isEqualTo(Result.ACCEPTED);
        assertThat(service.handle("raw")).isEqualTo(Result.DUPLICATE);
        assertThat(service.handle("raw")).isEqualTo(Result.DUPLICATE);

        assertThat(journal).hasSize(1);
        verify(reconciliation, times(1)).reconcileLocked(any(), any(), any());
    }

    @Test
    void twoDeliveriesRacingOnTheUniqueKeyCollapseToOne() {
        when(api.verifyWebhook("raw")).thenReturn(event(555L, 200000, "link-abc", "FT1"));
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("UQ_payment_provider_events_key")).when(events).saveAndFlush(any());

        assertThat(service.handle("raw")).isEqualTo(Result.DUPLICATE);
    }

    @Test
    void aPartialAmountProvesNothingAndOnlyTriggersAnAuthoritativeQuery() {
        when(api.verifyWebhook("raw")).thenReturn(event(555L, 50000, "link-abc", "FT2"));

        assertThat(service.handle("raw")).isEqualTo(Result.ACCEPTED);

        assertThat(row.getProviderStatus()).isNull();
        assertThat(row.getProviderAmountPaid()).isNull();
        assertThat(journal).singleElement().satisfies(e ->
                assertThat(e.getOutcome()).isEqualTo(PaymentProviderEvent.Outcome.TRIGGERED_SYNC));
        verify(reconciliation, never()).reconcileLocked(any(), any(), any());
        verify(flow).sync(7L, Trigger.PROVIDER_WEBHOOK);
    }

    @Test
    void aWebhookForAnotherLinkOfTheSameOrderCodeIsRejected() {
        when(api.verifyWebhook("raw")).thenReturn(event(555L, 200000, "someone-elses-link", "FT3"));

        assertThat(service.handle("raw")).isEqualTo(Result.ACCEPTED);

        assertThat(row.getProviderStatus()).isNull();
        assertThat(journal).singleElement().satisfies(e ->
                assertThat(e.getOutcome()).isEqualTo(PaymentProviderEvent.Outcome.REJECTED_MISMATCH));
        verify(reconciliation, never()).reconcileLocked(any(), any(), any());
    }

    @Test
    void aFailureOfTheFollowUpQueryDoesNotLoseTheJournaledEvent() {
        when(api.verifyWebhook("raw")).thenReturn(event(555L, 50000, "link-abc", "FT2"));
        org.mockito.Mockito.doThrow(new RuntimeException("payOS down")).when(flow).sync(eq(7L), any());

        assertThat(service.handle("raw")).isEqualTo(Result.ACCEPTED);
        assertThat(journal).hasSize(1);
    }

    @Test
    void differentTransactionsGetDifferentKeysAndTheSameOneTheSameKey() {
        String a = PayosWebhookService.eventKey(event(555L, 200000, "link-abc", "FT1"));
        String b = PayosWebhookService.eventKey(event(555L, 200000, "link-abc", "FT1"));
        String c = PayosWebhookService.eventKey(event(555L, 200000, "link-abc", "FT2"));
        assertThat(a).isEqualTo(b).isNotEqualTo(c).hasSize(64);
    }
}
