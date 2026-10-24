package com.oneshop.dto.response;

import com.oneshop.entity.PaymentRefundStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * The money side of an Order as the screens show it. Every refund is a SIMULATION: no bank was contacted.
 *
 * @param collected             money collected online (0 for COD / pay-at-store)
 * @param refunded              simulated refunds that were completed
 * @param retained              {@code collected - refunded}: what the shop keeps (the delivered goods and, when something
 *                              was delivered, the one shipping fee)
 * @param lines                 the refund ledger of the Order, oldest first
 * @param undeliverable         Fulfillment id to the customer-safe cause, for parts confirmed undeliverable
 * @param closedWithUndelivered the Order is COMPLETED although some part was confirmed undeliverable
 */
public record OrderRefundSummaryResponse(BigDecimal collected, BigDecimal refunded, BigDecimal retained, List<Line> lines,
                                         Map<Long, String> undeliverable, boolean closedWithUndelivered) {

    /** @param actor who decided (Admin screens only; {@code null} for the Customer) */
    public record Line(Long refundId, LocalDateTime at, String label, Long fulfillmentId, BigDecimal amount,
                       PaymentRefundStatus status, String reason, String actor) {
    }

    public boolean hasContent() {
        return !lines.isEmpty() || !undeliverable.isEmpty();
    }
}
