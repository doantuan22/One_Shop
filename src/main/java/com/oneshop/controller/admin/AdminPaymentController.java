package com.oneshop.controller.admin;

import com.oneshop.service.payment.AdminPaymentService;
import com.oneshop.service.payment.PaymentOutcome;
import com.oneshop.service.payment.RefundService;
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

    public AdminPaymentController(AdminPaymentService payments) {
        this.payments = payments;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("cases", payments.getCases(page));
        return "admin/payments/index";
    }

    @GetMapping("/{orderId:\\d+}")
    public String detail(@PathVariable Long orderId, Model model) {
        model.addAttribute("paymentCase", payments.getCase(orderId));
        return "admin/payments/detail";
    }

    @PostMapping("/{orderId:\\d+}/recheck")
    public String recheck(@PathVariable Long orderId) {
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

    public enum GatewayAnswer { COLLECTED, NOT_COLLECTED }
}
