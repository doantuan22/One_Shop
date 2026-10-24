package com.oneshop.controller.admin;

import com.oneshop.service.image.ImageCleanupService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Explicit operator retry; deliberately no automatic deletion worker or UI/theme change. */
@Controller
public class AdminImageCleanupController {
    private final ImageCleanupService cleanup;
    public AdminImageCleanupController(ImageCleanupService cleanup) { this.cleanup = cleanup; }

    @PostMapping("/admin/images/cleanup/retry")
    public String retry(@RequestParam(defaultValue = "50") int limit) {
        cleanup.retryPending(limit);
        return "redirect:/admin/products";
    }
}
