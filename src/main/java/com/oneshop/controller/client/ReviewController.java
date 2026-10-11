package com.oneshop.controller.client;

import com.oneshop.dto.request.ReviewRequest;
import com.oneshop.exception.BadRequestException;
import com.oneshop.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;

/**
 * A Customer reviews a Product bought in a COMPLETED Order (Roadmap V2 section 5). The reviewer is the authenticated
 * principal; the Service decides whether the Product was really received. The form lives on the Order detail and the
 * Product detail pages, so the user goes back to the page they came from. Error codes (not messages) travel in the
 * redirect so the URL cannot be used to show arbitrary text.
 */
@Controller
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/reviews")
    public String submit(@Valid @ModelAttribute ReviewRequest request, BindingResult bindingResult,
                         @RequestParam(required = false) Long orderId, Principal principal) {
        String back = orderId != null ? "/orders/" + orderId
                : request.productId() != null ? "/products/" + request.productId() : "/products";
        if (bindingResult.hasErrors()) {
            return "redirect:" + back + "?reviewError=invalid#reviews";
        }
        try {
            reviewService.submit(principal.getName(), request, orderId);
        } catch (BadRequestException ex) {
            boolean fieldProblem = "rating".equals(ex.getField()) || "comment".equals(ex.getField());
            return "redirect:" + back + "?reviewError=" + (fieldProblem ? "invalid" : "rejected") + "#reviews";
        }
        return "redirect:" + back + "?reviewed#reviews";
    }
}
