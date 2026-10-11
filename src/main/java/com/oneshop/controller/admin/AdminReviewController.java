package com.oneshop.controller.admin;

import com.oneshop.entity.VisibilityStatus;
import com.oneshop.exception.BadRequestException;
import com.oneshop.service.AdminReviewService;
import com.oneshop.service.ReviewService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin/reviews")
public class AdminReviewController {
    private final AdminReviewService reviews;
    private final ReviewService reviewService;
    public AdminReviewController(AdminReviewService reviews, ReviewService reviewService) {
        this.reviews = reviews; this.reviewService = reviewService;
    }
    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("reviews", reviews.getReviews(page)); return "admin/reviews";
    }
    @PostMapping("/{id}/status")
    public String status(@PathVariable Long id, @RequestParam VisibilityStatus status) {
        reviews.setStatus(id, status); return "redirect:/admin/reviews?success";
    }
    /** Admin may answer (or edit the answer of) any review. The Service trims and validates the text. */
    @PostMapping("/{id}/reply")
    public String reply(@PathVariable Long id, @RequestParam(required = false) String reply,
                        @RequestParam(defaultValue = "0") int page) {
        String back = "redirect:/admin/reviews?page=" + Math.max(0, page);
        try {
            reviewService.reply(id, reply);
        } catch (BadRequestException ex) {
            return back + "&replyError=invalid";
        }
        return back + "&replied";
    }
}
