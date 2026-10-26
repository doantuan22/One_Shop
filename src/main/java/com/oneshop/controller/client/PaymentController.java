package com.oneshop.controller.client;

import com.oneshop.dto.response.PaymentResponse;
import com.oneshop.service.PaymentService;
import com.oneshop.service.payment.PaymentOutcome;
import com.oneshop.service.payment.PaymentReconciliationService;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import java.security.Principal;

/**
 * Controlled internal demo: the browser only asks the gateway to enact a scenario; a payment status, amount or time is
 * never taken from it, and there is no endpoint that marks a payment successful or failed.
 */
@Controller
@RequestMapping("/orders/{orderId}/payments")
public class PaymentController {
    private final PaymentService paymentService;
    private final PaymentReconciliationService reconciliation;

    public PaymentController(PaymentService paymentService, PaymentReconciliationService reconciliation) {
        this.paymentService = paymentService;
        this.reconciliation = reconciliation;
    }

    @GetMapping
    public String view(@PathVariable Long orderId, Principal principal, Model model) {
        var info = paymentService.getOrderPayments(principal.getName(), orderId);
        if (info.expired() && info.orderStatus() == com.oneshop.entity.OrderStatus.PENDING_PAYMENT) {
            // the 15 minutes are over: cancel the unpaid order and give the stock back now, then show the real state
            reconciliation.reconcile(orderId, PaymentReconciliationService.Trigger.TIMEOUT_SCAN);
            info = paymentService.getOrderPayments(principal.getName(), orderId);
        }
        model.addAttribute("paymentOrder", info);
        return "payment/index";
    }

    @PostMapping("/attempts")
    public String start(@PathVariable Long orderId, Principal principal) {
        PaymentResponse attempt = paymentService.createOnlinePaymentAttempt(principal.getName(), orderId);
        // Demo gateway: pressing "pay" always succeeds; the ledger is still verified server side.
        PaymentOutcome outcome = reconciliation.simulate(principal.getName(), orderId, attempt.paymentId(),
                PaymentReconciliationService.SimulatedResult.SUCCESS);
        return "redirect:/orders/" + orderId + "/payments?outcome=" + outcome.name();
    }

    /**
     * The demo gateway enacts a scenario (SUCCESS / FAILED / UNKNOWN) for the customer's own attempt; the backend then
     * verifies its ledger. The request carries no payment status, amount or time: only which scenario to enact.
     */
    @PostMapping("/{paymentId}/simulate")
    public String simulate(@PathVariable Long orderId, @PathVariable Long paymentId,
                           @RequestParam("scenario") PaymentReconciliationService.SimulatedResult scenario,
                           Principal principal) {
        PaymentOutcome outcome = reconciliation.simulate(principal.getName(), orderId, paymentId, scenario);
        return "redirect:/orders/" + orderId + "/payments?outcome=" + outcome.name();
    }

    @ExceptionHandler(DataAccessException.class)
    public ModelAndView databaseError(DataAccessException ex) {
        ModelAndView view = new ModelAndView("error-message");
        view.setStatus(HttpStatus.CONFLICT);
        view.addObject("status", 409);
        view.addObject("error", "Không thể xử lý thanh toán");
        view.addObject("message", ex instanceof ConcurrencyFailureException
                ? "Đơn hàng đang được xử lý. Vui lòng tải lại trang và thử lại."
                : "Không thể lưu kết quả thanh toán. Vui lòng tải lại trang và thử lại.");
        return view;
    }
}
