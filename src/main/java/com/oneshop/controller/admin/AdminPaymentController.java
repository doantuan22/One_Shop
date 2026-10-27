package com.oneshop.controller.admin;

import com.oneshop.service.payment.AdminPaymentService;
import com.oneshop.service.payment.OrderRefundViewService;
import com.oneshop.service.payment.PaymentOutcome;
import com.oneshop.service.payment.RefundService;
import com.oneshop.service.payment.PaymentCaseCategory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Admin payment console (BE-05 task E). Fixed POSTs only: no route accepts an Order status, a payment status, an
 * amount or a time. The actor is the authenticated Administrator; the Service re-checks the role and the Order lock.
 */
@Controller
@RequestMapping("/admin/payments")
public class AdminPaymentController {

    private final AdminPaymentService payments;

    private final OrderRefundViewService refundView;

    private final com.oneshop.service.payment.ManualRefundService manualRefunds;
    private final org.springframework.beans.factory.ObjectProvider<com.oneshop.service.payment.PaymentEvidenceRefresher> refresher;

    public AdminPaymentController(AdminPaymentService payments, OrderRefundViewService refundView,
                                  com.oneshop.service.payment.ManualRefundService manualRefunds,
                                  org.springframework.beans.factory.ObjectProvider<com.oneshop.service.payment.PaymentEvidenceRefresher> refresher) {
        this.payments = payments;
        this.refundView = refundView;
        this.manualRefunds = manualRefunds;
        this.refresher = refresher;
    }

    /**
     * The console. {@code view}: attention (default), reconciling, waiting, paid, refunded, failed or all.
     * {@code q}: an Order id or a customer name / e-mail. Listing is read only.
     */
    @GetMapping
    public String list(@RequestParam(required = false) String view, @RequestParam(required = false) String q,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate from,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate to,
                        @RequestParam(defaultValue = "0") int page, Model model) {
        com.oneshop.service.query.SqlDayRange.of(from, to);
        boolean all = "all".equalsIgnoreCase(view == null ? "" : view.trim());
        PaymentCaseCategory selected = all ? null
                : view == null ? PaymentCaseCategory.ATTENTION
                : java.util.Objects.requireNonNullElse(PaymentCaseCategory.fromKey(view), PaymentCaseCategory.ATTENTION);
        model.addAttribute("cases", payments.getCases(selected, q, from, to, page));
        model.addAttribute("view", all ? "all" : selected.key());
        model.addAttribute("q", q == null ? "" : q.trim());
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("counts", payments.getCaseCounts());
        model.addAttribute("categories", java.util.Arrays.stream(PaymentCaseCategory.values())
                .filter(c -> c != PaymentCaseCategory.OTHER).toList());
        return "admin/payments/index";
    }

    @GetMapping("/{orderId:\\d+}")
    public String detail(@PathVariable Long orderId, Model model) {
        model.addAttribute("paymentCase", payments.getCase(orderId));
        model.addAttribute("refund", refundView.forAdmin(orderId));
        model.addAttribute("realRefunds", manualRefunds.pending(orderId));
        return "admin/payments/detail";
    }

    @PostMapping("/{orderId:\\d+}/recheck")
    public String recheck(@PathVariable Long orderId) {
        var provider = refresher.getIfAvailable();
        if (provider != null && !provider.refresh(orderId)) {
            // real money: never decide from stale evidence when the provider could not be asked
            return "redirect:/admin/payments/" + orderId + "?result=" + PaymentOutcome.RECONCILING.name();
        }
        PaymentOutcome outcome = payments.recheck(orderId);
        return "redirect:/admin/payments/" + orderId + "?result=" + outcome.name();
    }

    /** Mock gateway operator answer for an in-doubt attempt: {@code answer=COLLECTED} or {@code NOT_COLLECTED}. */
    @PostMapping("/{orderId:\\d+}/gateway")
    public String gateway(@PathVariable Long orderId, @RequestParam("answer") GatewayAnswer answer) {
        PaymentOutcome outcome = payments.settleGateway(orderId, answer == GatewayAnswer.COLLECTED);
        return "redirect:/admin/payments/" + orderId + "?result=" + outcome.name();
    }

    @PostMapping("/{orderId:\\d+}/refund")
    public String refund(@PathVariable Long orderId) {
        RefundService.Result result = payments.retryRefund(orderId);
        return "redirect:/admin/payments/" + orderId + "?refund=" + result.name();
    }

    /**
     * Records that a REAL refund was paid out of the bank. The amount is the requested one; the administrator supplies
     * the bank transaction code (evidence) and an optional note. Only this makes the refund REFUNDED.
     */
    @PostMapping("/{orderId:\\d+}/real-refunds/{refundId:\\d+}/confirm")
    public String confirmRealRefund(@PathVariable Long orderId, @PathVariable Long refundId,
                                    @RequestParam("evidenceReference") String evidenceReference,
                                    @RequestParam(value = "note", required = false) String note) {
        var result = manualRefunds.confirm(orderId, refundId, evidenceReference, note);
        return "redirect:/admin/payments/" + orderId + "?refund=" + result.name();
    }

    public enum GatewayAnswer { COLLECTED, NOT_COLLECTED }
}
