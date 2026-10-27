package com.oneshop.service.payment;

import com.oneshop.config.PaymentProperties;
import com.oneshop.repository.OrderRepository;
import com.oneshop.service.PaymentDeadlinePolicy;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** With real money the timeout scan asks the provider first and never cancels on stale evidence. */
class PaymentTimeoutRefreshTest {

    private final OrderRepository orders = mock(OrderRepository.class);
    private final PaymentReconciliationService reconciliation = mock(PaymentReconciliationService.class);
    private final PaymentEvidenceRefresher refresher = mock(PaymentEvidenceRefresher.class);
    private final PaymentTimeoutService service = new PaymentTimeoutService(orders, reconciliation,
            new PaymentProperties(), new PaymentDeadlinePolicy(new PaymentProperties(), Clock.systemDefaultZone()));

    private void oneCandidate() {
        when(orders.findPaymentCandidateIds(any(LocalDateTime.class), anyLong(), any(Pageable.class)))
                .thenReturn(List.of(5L)).thenReturn(List.of());
    }

    @Test
    void whenThePayosQueryFailsTheOrderIsLeftAloneForTheNextScan() {
        oneCandidate();
        when(refresher.refresh(5L)).thenReturn(false);
        service.setRefresher(refresher);

        var result = service.scan();

        assertThat(result.failed()).isEqualTo(1);
        verify(reconciliation, never()).reconcile(anyLong(), any());
    }

    @Test
    void whenPayosAnswersTheEngineDecidesFromTheFreshEvidence() {
        oneCandidate();
        when(refresher.refresh(5L)).thenReturn(true);
        when(reconciliation.reconcile(5L, PaymentReconciliationService.Trigger.TIMEOUT_SCAN))
                .thenReturn(PaymentOutcome.CANCELLED_TIMEOUT);
        service.setRefresher(refresher);

        var result = service.scan();

        assertThat(result.changed()).isEqualTo(1);
        verify(refresher).refresh(5L);
    }
}
