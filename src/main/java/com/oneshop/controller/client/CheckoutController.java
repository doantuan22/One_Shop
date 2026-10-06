package com.oneshop.controller.client;

import com.oneshop.dto.request.CheckoutRequest;
import com.oneshop.dto.request.StoreGroupCheckoutRequest;
import com.oneshop.dto.response.CheckoutResultResponse;
import com.oneshop.exception.BadRequestException;
import com.oneshop.service.CartService;
import com.oneshop.service.CheckoutService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Checkout of the logged-in customer ({@code /checkout/**} is CUSTOMER only, see SecurityConfig). The request names
 * the chosen cart lines and, per Store, how to receive and how to pay. It never carries a user, a price, a total or
 * a status: those come from the principal and from the database.
 */
@Controller
@RequestMapping("/checkout")
public class CheckoutController {

    private static final String VIEW = "checkout/index";
    private static final String BUSY = "Hệ thống đang xử lý nhiều đơn cho sản phẩm này. Vui lòng thử đặt hàng lại.";

    private final CheckoutService checkoutService;
    private final CartService cartService;

    public CheckoutController(CheckoutService checkoutService, CartService cartService) {
        this.checkoutService = checkoutService;
        this.cartService = cartService;
    }

    /** The checkout page for the lines ticked in the cart: one block per Store with its own choices. */
    @GetMapping
    public String form(@RequestParam(name = "cartItemIds", required = false) List<Long> cartItemIds,
                       Principal principal, Model model, HttpServletResponse response) {
        return checkoutPage(principal, model, response, cartItemIds, Map.of(), null);
    }

    /** Places the order. On any refusal nothing was created and the page says why. */
    @PostMapping
    public String placeOrder(@Valid @ModelAttribute CheckoutRequest request, BindingResult bindingResult,
                             @RequestParam(name = "cartItemIds", required = false) List<Long> cartItemIds,
                             Principal principal, Model model, HttpServletResponse response) {
        String error;
        if (bindingResult.hasErrors()) {
            error = bindingResult.getFieldErrors().stream().map(FieldError::getDefaultMessage).distinct()
                    .findFirst().orElse("Thông tin đặt hàng không hợp lệ.");
        } else {
            try {
                CheckoutResultResponse result = checkoutService.placeOrder(principal.getName(), request);
                return "redirect:/checkout/" + result.checkoutId() + "?success=placed";
            } catch (BadRequestException ex) {
                error = ex.getMessage();
            } catch (ConcurrencyFailureException ex) {
                // SQL Server chose this transaction as a deadlock victim or a lock wait ran out: nothing was kept
                error = BUSY;
            }
        }
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        // `request` is null when a value could not even be converted (for example an unknown fulfillment type)
        return checkoutPage(principal, model, response, cartItemIds, choicesOf(request), error);
    }

    /** What a checkout created: its Orders, one per Store. */
    @GetMapping("/{checkoutId}")
    public String result(@PathVariable Long checkoutId, Principal principal, Model model) {
        model.addAttribute("checkout", checkoutService.getCheckout(principal.getName(), checkoutId));
        return "checkout/result";
    }

    /**
     * Shows the checkout page for the given lines. If those lines cannot be checked out at all (not in the cart any
     * more, sold out, ...) the customer is shown their cart with the reason instead.
     */
    private String checkoutPage(Principal principal, Model model, HttpServletResponse response, List<Long> cartItemIds,
                                Map<Long, StoreGroupCheckoutRequest> choices, String error) {
        try {
            model.addAttribute("preview", checkoutService.prepare(principal.getName(), cartItemIds));
            model.addAttribute("choices", choices);
            model.addAttribute("checkoutError", error);
            return VIEW;
        } catch (BadRequestException ex) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("cart", cartService.getCart(principal.getName()));
            model.addAttribute("cartError", error != null ? error : ex.getMessage());
            return "cart/index";
        }
    }

    /** What the customer had chosen per Store, to show it again after a refusal. */
    private static Map<Long, StoreGroupCheckoutRequest> choicesOf(CheckoutRequest request) {
        Map<Long, StoreGroupCheckoutRequest> choices = new LinkedHashMap<>();
        if (request != null && request.groups() != null) {
            request.groups().stream().filter(group -> group != null && group.storeId() != null)
                    .forEach(group -> choices.putIfAbsent(group.storeId(), group));
        }
        return choices;
    }
}
