package com.oneshop.controller.client;

import com.oneshop.dto.request.AddCartItemRequest;
import com.oneshop.dto.request.UpdateCartItemRequest;
import com.oneshop.exception.BadRequestException;
import com.oneshop.service.CartService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.security.Principal;

/**
 * The cart of the logged-in customer ({@code /cart/**} is CUSTOMER only, see SecurityConfig). Whose cart it is always
 * comes from the authenticated principal: no request parameter names a user or a cart. Requests carry a
 * storeProductId or a cartItemId and a quantity, never a price.
 */
@Controller
@RequestMapping("/cart")
public class CartController {

    private static final String VIEW = "cart/index";
    private static final String INVALID_QUANTITY = "Số lượng không hợp lệ. Số lượng tối thiểu là 1.";

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public String view(Principal principal, Model model) {
        return cartPage(principal, model, null);
    }

    @PostMapping("/items")
    public String add(@Valid @ModelAttribute AddCartItemRequest request, BindingResult bindingResult,
                      Principal principal, Model model, HttpServletResponse response) {
        if (bindingResult.hasErrors()) {
            return rejected(principal, model, response, "Không thể thêm vào giỏ: thiếu sản phẩm hoặc số lượng không hợp lệ.");
        }
        try {
            cartService.addItem(principal.getName(), request);
        } catch (BadRequestException ex) {
            return rejected(principal, model, response, ex.getMessage());
        }
        return "redirect:/cart?success=added";
    }

    @PostMapping("/items/{cartItemId}")
    public String update(@PathVariable Long cartItemId, @Valid @ModelAttribute UpdateCartItemRequest request,
                         BindingResult bindingResult, Principal principal, Model model, HttpServletResponse response) {
        if (bindingResult.hasErrors()) {
            return rejected(principal, model, response, INVALID_QUANTITY);
        }
        try {
            cartService.updateItemQuantity(principal.getName(), cartItemId, request.quantity());
        } catch (BadRequestException ex) {
            return rejected(principal, model, response, ex.getMessage());
        }
        return "redirect:/cart?success=updated";
    }

    @PostMapping("/items/{cartItemId}/delete")
    public String remove(@PathVariable Long cartItemId, Principal principal) {
        cartService.removeItem(principal.getName(), cartItemId);
        return "redirect:/cart?success=removed";
    }

    /** The request was understood but refused: show the cart as it really is, with the reason. */
    private String rejected(Principal principal, Model model, HttpServletResponse response, String message) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        return cartPage(principal, model, message);
    }

    private String cartPage(Principal principal, Model model, String error) {
        model.addAttribute("cart", cartService.getCart(principal.getName()));
        model.addAttribute("cartError", error);
        return VIEW;
    }
}
