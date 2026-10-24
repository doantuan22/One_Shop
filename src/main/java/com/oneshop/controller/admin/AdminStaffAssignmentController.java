package com.oneshop.controller.admin;

import com.oneshop.dto.request.StaffAssignmentRequest;
import com.oneshop.dto.request.StaffTransferRequest;
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
    /** Staff name and e-mail for the search-box suggestions. */
    @GetMapping("/suggest")
    @ResponseBody
    public java.util.List<java.util.Map<String, String>> suggest() {
        return users.getStaffAccounts().stream()
                .map(u -> java.util.Map.of("sku", u.fullName(), "name", u.email()))
                .toList();
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
            try { assignments.assign(form); return "redirect:/admin/staff-assignments?success=assigned"; }
            catch (BadRequestException ex) { FormErrors.reject(errors, ex); }
        }
        return view(null, null, null, 0, model);
    }
    @PostMapping("/transfer")
    public String transfer(@Valid @ModelAttribute("transferForm") StaffTransferRequest form, BindingResult errors,
                           Model model) {
        if (!errors.hasErrors()) {
            try { assignments.transfer(form); return "redirect:/admin/staff-assignments?success=transferred"; }
            catch (BadRequestException ex) { FormErrors.reject(errors, ex); }
        }
        return view(null, null, null, 0, model);
    }
    @PostMapping("/{id}/status")
    public String status(@PathVariable Long id, @RequestParam ActiveStatus status,
                         @RequestParam(required = false) String reason,
                         @RequestParam(required = false) Long storeId,
                         @RequestParam(required = false) String q,
                         @RequestParam(required = false) ActiveStatus filterStatus,
                         org.springframework.web.servlet.mvc.support.RedirectAttributes redirect) {
        // the browser only supplies a rationale for deactivating; the record itself is the source of truth
        try {
            assignments.setStatus(id, status, reason);
        } catch (BadRequestException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/staff-assignments" + backTo(storeId, q, filterStatus).replaceFirst("^&", "?");
        }
        return "redirect:/admin/staff-assignments?success="
                + (status == ActiveStatus.ACTIVE ? "restored" : "suspended") + backTo(storeId, q, filterStatus);
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
        var working = assignments.getWorkingStaffIds();
        // a Staff who already works at a Store is moved with "Chuyển chi nhánh", never assigned a second time
        model.addAttribute("staff", users.getStaffAccounts().stream().filter(u -> !working.contains(u.id())).toList());
        model.addAttribute("workingStaffIds", working);
        model.addAttribute("stores", stores.getAllStores());
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new StaffAssignmentRequest(null, null, ActiveStatus.ACTIVE));
        }
        if (!model.containsAttribute("transferForm")) {
            model.addAttribute("transferForm", new StaffTransferRequest(null, null));
        }
        model.addAttribute("statuses", ActiveStatus.values()); model.addAttribute("storeId", storeId);
        model.addAttribute("q", q); model.addAttribute("status", status);
        return "admin/staff-assignments";
    }
}
