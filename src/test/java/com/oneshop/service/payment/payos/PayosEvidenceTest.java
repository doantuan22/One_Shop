package com.oneshop.service.payment.payos;

import com.oneshop.config.PayosProperties;
import com.oneshop.entity.Order;
import com.oneshop.entity.Payment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static com.oneshop.service.payment.payos.PayosTestSupport.order;
import static com.oneshop.service.payment.payos.PayosTestSupport.payos;
import static org.assertj.core.api.Assertions.assertThat;

class PayosEvidenceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-27T09:30:00Z"), ZoneOffset.UTC);

    private PayosEvidence evidence;
    private Payment row;

    @BeforeEach
    void setUp() {
        evidence = new PayosEvidence(new PayosProperties(), CLOCK);
        Order order = order(7L, LocalDateTime.of(2026, 10, 27, 9, 0, 0), "200000.00");
        row = payos(1, order, 123456789L);
        row.setProviderLinkId("link-abc");
    }

    private static PayosApi.LinkInfo info(long orderCode, long amount, long paid, String status, String time) {
        List<PayosApi.Transaction> tx = paid > 0 ? List.of(new PayosApi.Transaction("FT1", paid, time)) : List.of();
        return new PayosApi.LinkInfo("link-abc", orderCode, amount, paid, amount - paid, status, tx);
    }

    private static PayosApi.WebhookEvent webhook(long orderCode, long amount, String link, String code, String dataCode) {
        return new PayosApi.WebhookEvent(true, code, orderCode, amount, "VND", link, "FT9", "2026-10-27 16:10:00", dataCode);
    }

    @Test
    void aFullPaidAnswerStoresPaidAmountReferenceAndTheBankTimeInServerZone() {
        assertThat(evidence.applyLinkInfo(row, info(123456789L, 200000, 200000, "PAID", "2026-10-27 16:10:00"))).isTrue();
        assertThat(row.getProviderStatus()).isEqualTo("PAID");
        assertThat(row.getProviderAmountPaid()).isEqualTo(200000L);
        assertThat(row.getProviderReference()).isEqualTo("FT1");
        assertThat(row.getProviderPaidAt()).isEqualTo(LocalDateTime.of(2026, 10, 27, 9, 10, 0));
    }

    @Test
    void anAnswerForAnotherOrderCodeOrAmountIsIgnored() {
        assertThat(evidence.applyLinkInfo(row, info(999L, 200000, 200000, "PAID", "2026-10-27 16:10:00"))).isFalse();
        assertThat(evidence.applyLinkInfo(row, info(123456789L, 100000, 100000, "PAID", "2026-10-27 16:10:00"))).isFalse();
        assertThat(row.getProviderStatus()).isNull();
        assertThat(row.getProviderAmountPaid()).isNull();
    }

    @Test
    void aPaidRecordIsNeverDowngradedByALaterStaleAnswer() {
        evidence.applyLinkInfo(row, info(123456789L, 200000, 200000, "PAID", "2026-10-27 16:10:00"));
        evidence.applyLinkInfo(row, info(123456789L, 200000, 0, "PENDING", null));
        assertThat(row.getProviderStatus()).isEqualTo("PAID");
        assertThat(row.getProviderAmountPaid()).isEqualTo(200000L);
    }

    @Test
    void paidWithoutABankTimeUsesNowAsASafeUpperBound() {
        PayosApi.LinkInfo noTx = new PayosApi.LinkInfo("link-abc", 123456789L, 200000, 200000, 0, "PAID", List.of());
        evidence.applyLinkInfo(row, noTx);
        assertThat(row.getProviderPaidAt()).isEqualTo(LocalDateTime.of(2026, 10, 27, 9, 30, 0));
    }

    @Test
    void underpaymentKeepsTheAmountReceivedAndIsNotAFullPayment() {
        evidence.applyLinkInfo(row, info(123456789L, 200000, 50000, "UNDERPAID", "2026-10-27 16:10:00"));
        assertThat(row.getProviderStatus()).isEqualTo("UNDERPAID");
        assertThat(row.getProviderAmountPaid()).isEqualTo(50000L);
    }

    @Test
    void aSuccessfulWebhookForTheExactAmountIsFullPaymentEvidence() {
        assertThat(evidence.applyFullPaymentWebhook(row, webhook(123456789L, 200000, "link-abc", "00", "00"))).isTrue();
        assertThat(row.getProviderStatus()).isEqualTo("PAID");
        assertThat(row.getProviderPaidAt()).isEqualTo(LocalDateTime.of(2026, 10, 27, 9, 10, 0));
        assertThat(row.getProviderReference()).isEqualTo("FT9");
    }

    @Test
    void aWebhookThatDoesNotProveAFullPaymentChangesNothing() {
        // wrong amount (partial transfer), wrong order, wrong link, failure codes, other currency
        assertThat(evidence.applyFullPaymentWebhook(row, webhook(123456789L, 50000, "link-abc", "00", "00"))).isFalse();
        assertThat(evidence.applyFullPaymentWebhook(row, webhook(123456789L, 300000, "link-abc", "00", "00"))).isFalse();
        assertThat(evidence.applyFullPaymentWebhook(row, webhook(111L, 200000, "link-abc", "00", "00"))).isFalse();
        assertThat(evidence.applyFullPaymentWebhook(row, webhook(123456789L, 200000, "other-link", "00", "00"))).isFalse();
        assertThat(evidence.applyFullPaymentWebhook(row, webhook(123456789L, 200000, "link-abc", "01", "00"))).isFalse();
        assertThat(evidence.applyFullPaymentWebhook(row, webhook(123456789L, 200000, "link-abc", "00", "01"))).isFalse();
        PayosApi.WebhookEvent usd = new PayosApi.WebhookEvent(true, "00", 123456789L, 200000L, "USD", "link-abc",
                "FT9", "2026-10-27 16:10:00", "00");
        assertThat(evidence.applyFullPaymentWebhook(row, usd)).isFalse();
        PayosApi.WebhookEvent failed = new PayosApi.WebhookEvent(false, "00", 123456789L, 200000L, "VND", "link-abc",
                "FT9", "2026-10-27 16:10:00", "00");
        assertThat(evidence.applyFullPaymentWebhook(row, failed)).isFalse();
        assertThat(row.getProviderStatus()).isNull();
        assertThat(row.getProviderAmountPaid()).isNull();
    }
}
