package com.oneshop.controller.client;

import com.oneshop.service.PaymentService;
import com.oneshop.service.payment.PaymentOutcome;
import com.oneshop.service.payment.PaymentReconciliationService;
import com.oneshop.service.payment.payos.PayosPaymentFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.security.Principal;

/**
 * Where payOS sends the customer back ({@code returnUrl} / {@code cancelUrl}). These pages only DISPLAY and trigger a
 * query: whatever is in the query string ({@code status=PAID}, {@code cancel=false}, {@code code=00}...) is ignored.
 * The result comes from asking payOS for the link, then from the reconciliation engine. The cancel page does not
 * cancel the Order either: the customer may open a new link until the payment deadline, and money that moved anyway
 * is found by the same query.
 */
@Controller
@RequestMapping("/orders/{orderId}/payments/payos")
@ConditionalOnProperty(name = "oneshop.payment.provider", havingValue = "PAYOS")
public class PayosReturnController {

    private static final Logger log = LoggerFactory.getLogger(PayosReturnController.class);

    private final PaymentService paymentService;
    private final PayosPaymentFlow flow;

    public PayosReturnController(PaymentService paymentService, PayosPaymentFlow flow) {
        this.paymentService = paymentService;
        this.flow = flow;
    }

    @GetMapping("/return")
    public String returned(@PathVariable Long orderId, Principal principal) {
        return "redirect:/orders/" + orderId + "/payments?outcome=" + check(orderId, principal);
    }

    @GetMapping("/cancel")
    public String cancelled(@PathVariable Long orderId, Principal principal) {
        return "redirect:/orders/" + orderId + "/payments?payosCancelled&outcome=" + check(orderId, principal);
    }

    private String check(Long orderId, Principal principal) {
        paymentService.getOrderPayments(principal.getName(), orderId); // 404 unless the order is the caller's own
        try {
            return flow.sync(orderId, PaymentReconciliationService.Trigger.CUSTOMER_RETURN).name();
        } catch (RuntimeException ex) {
            log.warn("Reconciliation after the payOS return of order {} failed: {}", orderId, ex.toString());
            return PaymentOutcome.RECONCILING.name();
        }
    }
}
