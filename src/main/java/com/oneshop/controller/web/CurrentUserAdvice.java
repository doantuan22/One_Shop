package com.oneshop.controller.web;

import com.oneshop.entity.User;
import com.oneshop.repository.UserRepository;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.security.Principal;

/**
 * Cung cấp thông tin người dùng đang đăng nhập (${currentUser}) cho tất cả Thymeleaf views và SiteMesh decorators.
 */
@ControllerAdvice
public class CurrentUserAdvice {

    private final UserRepository userRepository;

    public CurrentUserAdvice(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @ModelAttribute("currentUser")
    public User currentUser(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            return null;
        }
        return userRepository.findByEmail(principal.getName()).orElse(null);
    }
}
