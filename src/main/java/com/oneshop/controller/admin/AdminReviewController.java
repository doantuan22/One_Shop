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
    private final com.oneshop.service.AdminUserService users;
    private final com.oneshop.service.ProductService products;
    public AdminReviewController(AdminReviewService reviews, com.oneshop.service.AdminUserService users,
                                 com.oneshop.service.ProductService products) {
        this.reviews = reviews;
        this.users = users;
        this.products = products;
    }

    /** Customers (name + e-mail) and products (SKU + name) for the search-box suggestions. */
    @GetMapping("/suggest")
    @ResponseBody
    public java.util.List<java.util.Map<String, String>> suggest() {
        var out = new java.util.ArrayList<java.util.Map<String, String>>(
                users.suggestions(com.oneshop.entity.RoleName.CUSTOMER));
        products.getAssignableProducts().forEach(p -> out.add(java.util.Map.of("sku", p.sku(), "name", p.name())));
        return out;
    }
    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(name = "status", required = false) VisibilityStatus status,
                       @RequestParam(required = false) Boolean replied,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("reviews", reviews.getReviews(page, q, status, replied));
        model.addAttribute("stats", reviews.stats());
        model.addAttribute("q", q);
        model.addAttribute("status", status);
        model.addAttribute("replied", replied);
        return "admin/reviews";
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
