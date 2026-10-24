package com.oneshop.controller.admin;

import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.FulfillmentConflictException;
import com.oneshop.service.fulfillment.AdminFulfillmentExceptionService;
import com.oneshop.service.fulfillment.AdminFulfillmentExceptionService.GoodsStatus;
import com.oneshop.service.fulfillment.AdminFulfillmentExceptionService.Outcome;
import com.oneshop.service.payment.PartialRefundService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * OPEN-10: the two decisions an Administrator can take on a failed delivery. Fixed POSTs only: no route accepts an Order
 * status, a Fulfillment status, a quantity, an amount or a time. The actor is the authenticated Administrator; the
 * Service checks the role again and applies the rules under the Order lock. The answer is a redirect back to the Order
 * with a short {@code exception} code (the app is stateless, so no flash attribute exists).
 */
@Controller
@RequestMapping("/admin/orders/{orderId}/fulfillments/{fulfillmentId}")
public class AdminFulfillmentExceptionController {

    private final AdminFulfillmentExceptionService exceptions;

    public AdminFulfillmentExceptionController(AdminFulfillmentExceptionService exceptions) {
        this.exceptions = exceptions;
    }

    @PostMapping("/redeliver")
    public String redeliver(@PathVariable Long orderId, @PathVariable Long fulfillmentId,
                            @RequestParam(required = false) String reason,
                            @RequestParam(required = false, defaultValue = "false") boolean goodsAtStore) {
        try {
            Outcome outcome = exceptions.redeliver(orderId, fulfillmentId, goodsAtStore, reason);
            return back(orderId, outcome.failedLeft() > 0 ? "redelivered-more" : "redelivered");
        } catch (FulfillmentConflictException ex) {
            return back(orderId, "refused:" + ex.getCode());
        } catch (BadRequestException ex) {
            return back(orderId, "invalid:" + ex.getField());
        }
    }

    @PostMapping("/undeliverable")
    public String undeliverable(@PathVariable Long orderId, @PathVariable Long fulfillmentId,
                                @RequestParam(required = false) String reason,
                                @RequestParam(required = false) String goods) {
        try {
            Outcome outcome = exceptions.markUndeliverable(orderId, fulfillmentId, GoodsStatus.fromKey(goods), reason);
            if (outcome.kind() == AdminFulfillmentExceptionService.Kind.AWAITING_VERIFICATION) {
                return back(orderId, "pending-verification");
            }
            if (outcome.refund() != null && outcome.refund().status() == PartialRefundService.Status.SIMULATION_FAILED) {
                return back(orderId, "refund-failed");
            }
            if (outcome.failedLeft() > 0) {
                return back(orderId, "undeliverable-more");
            }
            if (outcome.orderClosed()) {
                return back(orderId, "closed");
            }
            return back(orderId, outcome.partiallyClosed() ? "closed-partial" : "undeliverable-resumed");
        } catch (FulfillmentConflictException ex) {
            return back(orderId, "refused:" + ex.getCode());
        } catch (BadRequestException ex) {
            return back(orderId, "invalid:" + ex.getField());
        }
    }

    private static String back(Long orderId, String code) {
        return "redirect:/admin/orders/" + orderId + "?exception=" + code;
    }
}
