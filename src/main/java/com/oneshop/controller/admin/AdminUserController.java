package com.oneshop.controller.admin;

import com.oneshop.dto.request.AdminUserRequest;
import com.oneshop.entity.*;
import com.oneshop.exception.BadRequestException;
import com.oneshop.service.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin/users")
public class AdminUserController {
    private final AdminUserService users;
    public AdminUserController(AdminUserService users) { this.users = users; }
    @ModelAttribute("roles") public RoleName[] roles() { return RoleName.values(); }
    @ModelAttribute("statuses") public ActiveStatus[] statuses() { return ActiveStatus.values(); }
    @GetMapping
    public String list(@RequestParam(required = false) RoleName role, @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("users", users.getUsers(role, page)); model.addAttribute("role", role); return "admin/users";
    }
    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("form", new AdminUserRequest("", "", "", RoleName.STAFF, ActiveStatus.ACTIVE, ""));
        model.addAttribute("current", null); return "admin/user-form";
    }
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        var user = users.getUser(id);
        model.addAttribute("current", user);
        model.addAttribute("form", new AdminUserRequest(user.email(), user.fullName(), user.phone(), user.role(), user.status(), ""));
        return "admin/user-form";
    }
    @PostMapping
    public String create(@Valid @ModelAttribute("form") AdminUserRequest form, BindingResult errors, Model model) {
        if (!errors.hasErrors()) {
            try { users.create(form); return "redirect:/admin/users?success"; }
            catch (BadRequestException ex) { FormErrors.reject(errors, ex); }
        }
        model.addAttribute("current", null); return "admin/user-form";
    }
    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") AdminUserRequest form,
                         BindingResult errors, Model model) {
        var user = users.getUser(id);
        if (!errors.hasErrors()) {
            try { users.update(id, form); return "redirect:/admin/users?success"; }
            catch (BadRequestException ex) { FormErrors.reject(errors, ex); }
        }
        model.addAttribute("current", user); return "admin/user-form";
    }
}
