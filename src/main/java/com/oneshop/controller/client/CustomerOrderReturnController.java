package com.oneshop.controller.client;

import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.ImageStorageException;
import com.oneshop.service.returns.CustomerOrderReturnService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.List;

/**
 * Return of a received Order: the form ({@code GET}) and the request ({@code POST}, multipart). The Customer sends only
 * a reason and photos; the Order, the lines, the amount and the deadline are all decided by the Service. The answer to
 * a refused request is a redirect back to the form with a short {@code error} code.
 */
@Controller
@RequestMapping("/orders/{orderId}/return")
public class CustomerOrderReturnController {

    private final CustomerOrderReturnService returns;
    private final com.oneshop.service.CustomerOrderService customerOrders;

    public CustomerOrderReturnController(CustomerOrderReturnService returns, com.oneshop.service.CustomerOrderService customerOrders) {
        this.returns = returns;
        this.customerOrders = customerOrders;
    }

    @GetMapping
    public String form(@PathVariable Long orderId, Principal principal, Model model) {
        if (returns.find(principal.getName(), orderId).isPresent()) {
            return "redirect:/orders/" + orderId;
        }
        model.addAttribute("orderId", orderId);
        model.addAttribute("orderNumber", customerOrders.getOrderNumbers(principal.getName()).get(orderId));
        model.addAttribute("eligibility", returns.eligibility(principal.getName(), orderId));
        model.addAttribute("windowDays", CustomerOrderReturnService.RETURN_WINDOW.toDays());
        model.addAttribute("maxImages", CustomerOrderReturnService.MAX_IMAGES);
        model.addAttribute("minReason", CustomerOrderReturnService.MIN_REASON);
        model.addAttribute("maxReason", CustomerOrderReturnService.MAX_REASON);
        return "orders/return-form";
    }

    @PostMapping(consumes = "multipart/form-data")
    public String request(@PathVariable Long orderId, Principal principal,
                          @RequestParam(required = false) String reason,
                          @RequestParam(name = "images", required = false) List<MultipartFile> images) {
        try {
            returns.request(principal.getName(), orderId, reason, images);
            return "redirect:/orders/" + orderId + "?returned";
        } catch (ImageStorageException ex) {
            return back(orderId, "upload");
        } catch (BadRequestException ex) {
            String field = ex.getField();
            return back(orderId, "reason".equals(field) || "images".equals(field) ? field : "refused");
        } catch (DataIntegrityViolationException ex) {
            // a second request for the same Order lost the race against the unique key
            return back(orderId, "refused");
        }
    }

    private static String back(Long orderId, String code) {
        return "redirect:/orders/" + orderId + "/return?error=" + code;
    }
}
