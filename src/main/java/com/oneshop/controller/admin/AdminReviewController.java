package com.oneshop.controller.admin;

import com.oneshop.entity.VisibilityStatus;
import com.oneshop.service.AdminReviewService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin/reviews")
public class AdminReviewController {
    private final AdminReviewService reviews;
    public AdminReviewController(AdminReviewService reviews) { this.reviews = reviews; }
    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("reviews", reviews.getReviews(page)); return "admin/reviews";
    }
    @PostMapping("/{id}/status")
    public String status(@PathVariable Long id, @RequestParam VisibilityStatus status) {
        reviews.setStatus(id, status); return "redirect:/admin/reviews?success";
    }
}
