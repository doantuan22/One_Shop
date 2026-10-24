package com.oneshop.dto.response;

import com.oneshop.entity.FulfillmentType;
import com.oneshop.entity.OrderPaymentStatus;
import com.oneshop.entity.OrderStatus;
import com.oneshop.entity.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * What an Administrator needs to decide a failed delivery (OPEN-10 / OPEN-10.1): the Order's money position, and for
 * every FAILED Fulfillment its Store, goods, stock now, the reason Staff gave, what is known about the goods, its history,
 * what a decision would refund (simulated) and which decisions are open (and why not when one is closed). Read only; it
 * never carries a code a customer holds.
 *
 * @param money  amounts of the whole Order: collected, already refunded (simulated), kept, delivered, not delivered
 * @param failed the Fulfillments still FAILED (each needs one decision); empty when nothing is waiting
 */
public record FulfillmentExceptionResponse(Long orderId, OrderStatus orderStatus, PaymentMethod paymentMethod,
                                           OrderPaymentStatus paymentStatus, Money money, List<Part> failed) {

    /**
     * @param total       {@code orders.total_amount} (goods plus the one shipping fee)
     * @param collected   money really collected: the ONLINE gateway collection, or the COD / pay-at-store cash
     * @param refunded    simulated refunds already recorded in the refund ledger
     * @param retained    {@code collected - refunded}: what the shop keeps
     * @param delivered   value of the goods of COMPLETED Fulfillments
     * @param undelivered value of the goods of FAILED Fulfillments
     * @param inProgress  value of the goods of Fulfillments that are neither COMPLETED, FAILED nor CANCELLED
     */
    public record Money(BigDecimal total, BigDecimal collected, BigDecimal refunded, BigDecimal retained,
                        BigDecimal delivered, BigDecimal undelivered, BigDecimal inProgress) {
    }

    /**
     * @param failureReason         the reason Staff wrote when they reported the failure
     * @param goodsStatus           what the Administrator last recorded about the goods ({@code RETURNED}, {@code LOST},
     *                              {@code UNCONFIRMED}) or {@code null} when nothing was recorded yet
     * @param awaitingVerification  the goods were recorded as UNCONFIRMED: the exception stays open until they are known
     * @param refundGoods           simulated refund of the goods of this part if it is confirmed undeliverable now
     * @param refundShipping        simulated refund of the shipping fee in that case ({@code 0} when it is kept)
     * @param canRedeliver          decision A is open
     * @param redeliverBlocked      why A is closed, or {@code null}
     * @param canMarkUndeliverable  decision B is open
     * @param undeliverableBlocked  why B is closed, or {@code null}
     */
    public record Part(Long fulfillmentId, Long storeId, String storeName, boolean storeActive, FulfillmentType type,
                       String failureReason, LocalDateTime failedAt, String failedBy, String goodsStatus,
                       boolean awaitingVerification, List<Line> lines, BigDecimal value,
                       BigDecimal refundGoods, BigDecimal refundShipping, List<FulfillmentHistoryResponse> history,
                       boolean canRedeliver, String redeliverBlocked, boolean canMarkUndeliverable,
                       String undeliverableBlocked) {
    }

    /** @param stockNow the quantity the Store holds of this SKU right now (Admin sees exact stock) */
    public record Line(String productName, int quantity, BigDecimal subtotal, int stockNow) {
    }
}
