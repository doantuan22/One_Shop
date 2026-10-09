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
    public String list(@RequestParam(required = false) Long storeId, @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("form", new StaffAssignmentRequest(null, storeId, ActiveStatus.ACTIVE));
        return view(storeId, page, model);
    }
    @PostMapping
    public String assign(@Valid @ModelAttribute("form") StaffAssignmentRequest form, BindingResult errors, Model model) {
        if (!errors.hasErrors()) {
            try { assignments.assign(form); return "redirect:/admin/staff-assignments?success"; }
            catch (BadRequestException ex) { FormErrors.reject(errors, ex); }
        }
        return view(null, 0, model);
    }
    @PostMapping("/{id}/status")
    public String status(@PathVariable Long id, @RequestParam ActiveStatus status) {
        assignments.setStatus(id, status); return "redirect:/admin/staff-assignments?success";
    }
    private String view(Long storeId, int page, Model model) {
        model.addAttribute("assignments", assignments.getAssignments(storeId, page));
        model.addAttribute("staff", users.getStaffAccounts()); model.addAttribute("stores", stores.getAllStores());
        model.addAttribute("statuses", ActiveStatus.values()); model.addAttribute("storeId", storeId);
        return "admin/staff-assignments";
    }
}
