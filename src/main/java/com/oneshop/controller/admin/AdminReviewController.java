package com.oneshop.controller.admin;

import com.oneshop.entity.VisibilityStatus;
import com.oneshop.service.AdminReviewService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.AccessDeniedException;

@Controller
@RequestMapping("/admin/reviews")
public class AdminReviewController {
    private final AdminReviewService reviews;
    public AdminReviewController(AdminReviewService reviews) {
        this.reviews = reviews;
    }
    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("reviews", reviews.getReviews(page)); return "admin/reviews";
    }
    @PostMapping("/{id}/status")
    public String status(@PathVariable Long id, @RequestParam VisibilityStatus status) {
        reviews.setStatus(id, status); return "redirect:/admin/reviews?success";
    }
    /** OPEN-06 decided: retained URL fails closed for old forms/bookmarks. Admin moderates only. */
    @PostMapping("/{id}/reply")
    public String reply(@PathVariable Long id, @RequestParam(required = false) String reply,
                        @RequestParam(defaultValue = "0") int page) {
        throw new AccessDeniedException("Admin chỉ kiểm duyệt đánh giá; phản hồi thuộc nhân viên chi nhánh nguồn.");
    }
}
