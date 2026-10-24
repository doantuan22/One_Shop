package com.oneshop.controller.client;

import com.oneshop.dto.request.ForgotPasswordRequest;
import com.oneshop.dto.request.ResetPasswordRequest;
import com.oneshop.exception.BadRequestException;
import com.oneshop.service.PasswordResetService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Forgot password, in two steps and open to guests: (1) the e-mail of an existing account receives a one-time code,
 * (2) the code plus a new password reset the password of that same account. Passwords and codes are never sent
 * back to the page.
 */
@Controller
@RequestMapping("/forgot-password")
public class PasswordResetController {

    private final PasswordResetService service;

    public PasswordResetController(PasswordResetService service) {
        this.service = service;
    }

    @GetMapping
    public String requestForm(@RequestParam(name = "email", required = false) String email, Model model) {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail(email);
        model.addAttribute("forgotPasswordRequest", request);
        return "auth/forgot-password";
    }

    @PostMapping
    public String sendCode(@Valid @ModelAttribute("forgotPasswordRequest") ForgotPasswordRequest request,
                           BindingResult bindingResult) {
        if (!bindingResult.hasErrors()) {
            try {
                service.requestOtp(request.getEmail());
                return "redirect:" + UriComponentsBuilder.fromPath("/forgot-password/reset")
                        .queryParam("email", PasswordResetService.normalize(request.getEmail()))
                        .queryParam("sent", "").encode().build().toUriString();
            } catch (BadRequestException ex) {
                bindingResult.rejectValue("email", "invalid", ex.getMessage());
            }
        }
        return "auth/forgot-password";
    }

    @GetMapping("/reset")
    public String resetForm(@RequestParam(name = "email", required = false) String email, Model model) {
        if (email == null || email.isBlank()) {
            return "redirect:/forgot-password";
        }
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setEmail(PasswordResetService.normalize(email));
        model.addAttribute("resetPasswordRequest", request);
        return "auth/reset-password";
    }

    @PostMapping("/reset")
    public String reset(@Valid @ModelAttribute("resetPasswordRequest") ResetPasswordRequest request,
                        BindingResult bindingResult) {
        if (!bindingResult.hasErrors()) {
            try {
                service.resetPassword(request.getEmail(), request.getOtp(), request.getNewPassword(),
                        request.getConfirmPassword());
                return "redirect:/login?passwordReset";
            } catch (BadRequestException ex) {
                String field = switch (String.valueOf(ex.getField())) {
                    case "otp", "confirmPassword", "newPassword" -> ex.getField();
                    default -> "email";
                };
                bindingResult.rejectValue(field, "invalid", ex.getMessage());
            }
        }
        request.setOtp(null);
        request.setNewPassword(null);
        request.setConfirmPassword(null);
        return "auth/reset-password";
    }
}
