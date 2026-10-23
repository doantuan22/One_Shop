package com.oneshop.controller.admin;

import com.oneshop.dto.request.StaffAssignmentRequest;
import com.oneshop.entity.ActiveStatus;
import com.oneshop.exception.BadRequestException;
import com.oneshop.service.*;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin/staff-assignments")
public class AdminStaffAssignmentController {
    private final AdminStaffAssignmentService assignments;
    private final AdminUserService users;
    private final StoreService stores;
    public AdminStaffAssignmentController(AdminStaffAssignmentService assignments, AdminUserService users, StoreService stores) {
        this.assignments = assignments; this.users = users; this.stores = stores;
    }
    @GetMapping
    public String list(@RequestParam(required = false) Long storeId,
                       @RequestParam(required = false) String q,
                       @RequestParam(required = false) ActiveStatus status,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("form", new StaffAssignmentRequest(null, storeId, ActiveStatus.ACTIVE));
        return view(storeId, q, status, page, model);
    }
    @PostMapping
    public String assign(@Valid @ModelAttribute("form") StaffAssignmentRequest form, BindingResult errors, Model model) {
        if (!errors.hasErrors()) {
            try { assignments.assign(form); return "redirect:/admin/staff-assignments?success"; }
            catch (BadRequestException ex) { FormErrors.reject(errors, ex); }
        }
        return view(null, null, null, 0, model);
    }
    @PostMapping("/{id}/status")
    public String status(@PathVariable Long id, @RequestParam ActiveStatus status,
                         @RequestParam(required = false) String reason,
                         @RequestParam(required = false) Long storeId,
                         @RequestParam(required = false) String q,
                         @RequestParam(required = false) ActiveStatus filterStatus) {
        // the browser only supplies a rationale for deactivating; the record itself is the source of truth
        assignments.setStatus(id, status, reason);
        return "redirect:/admin/staff-assignments?success" + backTo(storeId, q, filterStatus);
    }
    private static String backTo(Long storeId, String q, ActiveStatus filterStatus) {
        var params = new StringBuilder();
        if (storeId != null) params.append("&storeId=").append(storeId);
        if (q != null && !q.isBlank()) params.append("&q=").append(q.trim());
        if (filterStatus != null) params.append("&status=").append(filterStatus);
        return params.toString();
    }
    private String view(Long storeId, String q, ActiveStatus status, int page, Model model) {
        model.addAttribute("assignments", assignments.searchAssignments(storeId, q, status, page));
        model.addAttribute("staff", users.getStaffAccounts()); model.addAttribute("stores", stores.getAllStores());
        model.addAttribute("statuses", ActiveStatus.values()); model.addAttribute("storeId", storeId);
        model.addAttribute("q", q); model.addAttribute("status", status);
        return "admin/staff-assignments";
    }
}
