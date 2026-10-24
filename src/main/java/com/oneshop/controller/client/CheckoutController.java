package com.oneshop.controller.client;

import com.oneshop.dto.request.PlaceOrderRequest;
import com.oneshop.dto.response.CheckoutResultResponse;
import com.oneshop.dto.response.OrderSummaryResponse;
import com.oneshop.entity.FulfillmentType;
import com.oneshop.entity.PaymentMethod;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.CheckoutConflictException;
import com.oneshop.service.CartService;
import com.oneshop.service.CheckoutService;
import com.oneshop.service.PaymentService;
import com.oneshop.service.payment.PaymentReconciliationService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.util.List;

/**
 * Checkout of the logged-in customer ({@code /checkout/**} is CUSTOMER only, see SecurityConfig). The request names
 * the chosen cart lines and how to receive and pay for the whole order. It never carries a user, a Store for
 * delivery, a price, a total or a status: those come from the principal and from the database. A pickup Store may be
 * named and is validated again by the service.
 */
@Controller
@RequestMapping("/checkout")
public class CheckoutController {

    private static final String VIEW = "checkout/index";
    private static final String BUSY = "Hệ thống đang xử lý nhiều đơn cho sản phẩm này. Vui lòng thử đặt hàng lại.";

    private final CheckoutService checkoutService;
    private final CartService cartService;

    private final PaymentService paymentService;
    private final PaymentReconciliationService reconciliation;

    public CheckoutController(CheckoutService checkoutService, CartService cartService,
                              PaymentService paymentService, PaymentReconciliationService reconciliation) {
        this.checkoutService = checkoutService;
        this.cartService = cartService;
        this.paymentService = paymentService;
        this.reconciliation = reconciliation;
    }

    /** Demo gateway: an ONLINE order is paid successfully the moment it is placed (no real money moves). */
    private void settleOnlineOrders(String email, CheckoutResultResponse result) {
        for (OrderSummaryResponse order : result.orders()) {
            if (order.paymentMethod() != PaymentMethod.ONLINE) {
                continue;
            }
            try {
                var attempt = paymentService.createOnlinePaymentAttempt(email, order.orderId());
                reconciliation.simulate(email, order.orderId(), attempt.paymentId(),
                        PaymentReconciliationService.SimulatedResult.SUCCESS);
            } catch (RuntimeException ex) {
                // the order stays placed and payable; the customer can still pay from the payment page
            }
        }
    }

    /** The checkout page for the lines ticked in the cart, for the way to receive and pay chosen so far. */
    @GetMapping
    public String form(@RequestParam(name = "cartItemIds", required = false) List<Long> cartItemIds,
                       @RequestParam(name = "fulfillmentType", required = false) FulfillmentType fulfillmentType,
                       @RequestParam(name = "paymentMethod", required = false) PaymentMethod paymentMethod,
                       @RequestParam(name = "pickupStoreId", required = false) Long pickupStoreId,
                       @RequestParam(name = "shippingAddress", required = false) String shippingAddress,
                       @RequestParam(name = "receiverName", required = false) String receiverName,
                       @RequestParam(name = "receiverPhone", required = false) String receiverPhone,
                       @RequestParam(name = "promotionCode", required = false) String promotionCode,
                       Principal principal, Model model, HttpServletResponse response) {
        PlaceOrderRequest edited = receiverName == null && receiverPhone == null ? null
                : new PlaceOrderRequest(cartItemIds, fulfillmentType, paymentMethod, receiverName, receiverPhone,
                shippingAddress, pickupStoreId, false, null, null, promotionCode);
        return checkoutPage(principal, model, response, cartItemIds, fulfillmentType, paymentMethod, pickupStoreId,
                shippingAddress, promotionCode, edited, null, null);
    }

    /** Places the order. On any refusal nothing was created and the page says why, with a fresh preview. */
    @PostMapping
    public String placeOrder(@Valid @ModelAttribute PlaceOrderRequest request, BindingResult bindingResult,
                             @RequestParam(name = "cartItemIds", required = false) List<Long> cartItemIds,
                             Principal principal, Model model, HttpServletResponse response) {
        String error;
        String code = null;
        if (bindingResult.hasErrors()) {
            error = bindingResult.getFieldErrors().stream().map(FieldError::getDefaultMessage).distinct()
                    .findFirst().orElse("Thông tin đặt hàng không hợp lệ.");
        } else {
            try {
                CheckoutResultResponse result = checkoutService.placeOrder(principal.getName(), request);
                settleOnlineOrders(principal.getName(), result);
                return "redirect:/checkout/" + result.checkoutId() + "?success=placed";
            } catch (CheckoutConflictException ex) {
                error = ex.getMessage();
                code = ex.getCode();
            } catch (BadRequestException ex) {
                error = ex.getMessage();
            } catch (ConcurrencyFailureException ex) {
                // SQL Server chose this transaction as a deadlock victim or a lock wait ran out: nothing was kept
                error = BUSY;
            }
        }
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        // `request` is null when a value could not even be converted (for example an unknown fulfillment type)
        return checkoutPage(principal, model, response, cartItemIds,
                request == null ? null : request.fulfillmentType(),
                request == null ? null : request.paymentMethod(),
                request == null ? null : request.pickupStoreId(),
                request == null ? null : request.shippingAddress(), request == null ? null : request.promotionCode(),
                request, error, code);
    }

    /** What a checkout created: its Order(s), and for each Order the Fulfillment of every supplying Store. */
    @GetMapping("/{checkoutId}")
    public String result(@PathVariable Long checkoutId, Principal principal, Model model) {
        model.addAttribute("checkout", checkoutService.getCheckout(principal.getName(), checkoutId));
        return "checkout/result";
    }

    /**
     * Shows the checkout page for the given lines. If those lines cannot be checked out at all (not in the cart any
     * more, sold out, ...) the customer is shown their cart with the reason instead.
     */
    private String checkoutPage(Principal principal, Model model, HttpServletResponse response,
                                List<Long> cartItemIds, FulfillmentType fulfillmentType, PaymentMethod paymentMethod,
                                Long pickupStoreId, String shippingAddress, String promotionCode,
                                PlaceOrderRequest submitted, String error, String errorCode) {
        try {
            model.addAttribute("preview", checkoutService.preview(principal.getName(), cartItemIds, fulfillmentType,
                    paymentMethod, pickupStoreId, shippingAddress, promotionCode));
            model.addAttribute("form", submitted);
            model.addAttribute("checkoutError", error);
            model.addAttribute("checkoutErrorCode", errorCode);
            return VIEW;
        } catch (BadRequestException ex) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("cart", cartService.getCart(principal.getName()));
            model.addAttribute("cartError", error != null ? error : ex.getMessage());
            return "cart/index";
        }
    }
}
