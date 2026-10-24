package com.oneshop.controller.client;

import com.oneshop.dto.request.UpdateAccountRequest;
import com.oneshop.entity.User;
import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.repository.CustomerAddressRepository;
import com.oneshop.repository.UserRepository;
import com.oneshop.service.AccountProfileService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.security.Principal;

/**
 * Quản lý trang Thông tin cá nhân của người dùng đang đăng nhập.
 * Thông tin chỉ được truy xuất và cập nhật dựa trên Principal (người dùng hiện tại).
 * Tên đăng nhập (username) và Email là thông tin định danh bất biến, chỉ được xem,
 * bị khóa hoàn toàn và không thể chỉnh sửa dưới mọi hình thức.
 */
@Controller
@RequestMapping("/account")
@PreAuthorize("isAuthenticated()")
public class AccountController {

    private final UserRepository userRepository;
    private final CustomerAddressRepository addressRepository;
    private final AccountProfileService profiles;

    public AccountController(UserRepository userRepository, CustomerAddressRepository addressRepository,
                             AccountProfileService profiles) {
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.profiles = profiles;
    }

    @GetMapping
    public String profile(Principal principal, Model model) {
        User user = getUser(principal);
        populateModel(user, model);
        if (!model.containsAttribute("updateAccountRequest")) {
            model.addAttribute("updateAccountRequest", new UpdateAccountRequest(
                    user.getFullName(),
                    user.getPhone()
            ));
        }
        return "account/profile";
    }

    @PostMapping
    public String updateProfile(Principal principal,
                                @Valid @ModelAttribute("updateAccountRequest") UpdateAccountRequest request,
                                BindingResult bindingResult,
                                Model model) {
        if (bindingResult.hasErrors()) {
            populateModel(getUser(principal), model);
            return "account/profile";
        }

        if (principal == null) throw new ResourceNotFoundException("Chưa đăng nhập.");
        profiles.update(principal.getName(), request);

        return "redirect:/account?success";
    }

    private User getUser(Principal principal) {
        if (principal == null) {
            throw new ResourceNotFoundException("Chưa đăng nhập.");
        }
        return userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin tài khoản."));
    }

    private void populateModel(User user, Model model) {
        model.addAttribute("accountUser", user);
        model.addAttribute("addresses", addressRepository.findByUserEmailOrderByDefaultAddressDescCreatedAtDesc(user.getEmail()));
    }
}
