package com.oneshop.dto.response;

import com.oneshop.entity.OrderFulfillmentStatus;

import java.util.List;
import java.util.Map;

/**
 * Everything an administrator needs to decide a payment case: the Order, every ONLINE payment row with its ledger
 * state, the gateway's current answer, the parts and the status history. The actions the page may offer are
 * computed on the server; none of them lets an administrator pick a status.
 *
 * @param partStatuses part id to status (no Store data beyond what the order detail already shows)
 */
public record AdminPaymentCaseDetailResponse(AdminPaymentCaseResponse summary, OrderDetailResponse detail,
                                             List<AdminPaymentAttemptResponse> attempts,
                                             Map<Long, OrderFulfillmentStatus> partStatuses, boolean canRecheck,
                                             boolean canSettleGateway, boolean canRetryRefund,
                                             boolean gatewayEnabled) {
}
