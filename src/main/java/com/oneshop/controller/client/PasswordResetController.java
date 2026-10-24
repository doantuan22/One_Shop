package com.oneshop.controller.client;

import com.oneshop.dto.request.ForgotPasswordRequest;
import com.oneshop.dto.request.ResetPasswordRequest;
import com.oneshop.dto.request.VerifyOtpRequest;
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
 * Forgot password, open to guests, in three steps that can only be taken in order:
 * (1) the e-mail of an existing account receives a one-time code,
 * (2) typing that code correctly is what opens the reset page,
 * (3) the new password is accepted only with the ticket step 2 issued.
 * Codes and passwords are never sent back to the page.
 */
@Controller
@RequestMapping("/forgot-password")
public class PasswordResetController {

    private final PasswordResetService service;

    public PasswordResetController(PasswordResetService service) {
        this.service = service;
    }

    // ------------------------------------------------------------------ step 1: e-mail

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
                return "redirect:" + verifyUrl(request.getEmail(), "sent");
            } catch (BadRequestException ex) {
                bindingResult.rejectValue("email", "invalid", ex.getMessage());
            }
        }
        return "auth/forgot-password";
    }

    // ------------------------------------------------------------------ step 2: the code

    @GetMapping("/verify")
    public String verifyForm(@RequestParam(name = "email", required = false) String email, Model model) {
        if (email == null || email.isBlank()) {
            return "redirect:/forgot-password";
        }
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail(PasswordResetService.normalize(email));
        model.addAttribute("verifyOtpRequest", request);
        return "auth/verify-otp";
    }

    /** A right code opens the reset page right here (no redirect, so the ticket never appears in a URL). */
    @PostMapping("/verify")
    public String verify(@Valid @ModelAttribute("verifyOtpRequest") VerifyOtpRequest request,
                         BindingResult bindingResult, Model model) {
        if (!bindingResult.hasErrors()) {
            try {
                String ticket = service.verifyOtp(request.getEmail(), request.getOtp());
                ResetPasswordRequest reset = new ResetPasswordRequest();
                reset.setEmail(PasswordResetService.normalize(request.getEmail()));
                reset.setTicket(ticket);
                model.addAttribute("resetPasswordRequest", reset);
                return "auth/reset-password";
            } catch (BadRequestException ex) {
                bindingResult.rejectValue("email".equals(ex.getField()) ? "email" : "otp", "invalid", ex.getMessage());
            }
        }
        request.setOtp(null);
        return "auth/verify-otp";
    }

    // ------------------------------------------------------------------ step 3: the new password

    /** The reset page is reachable only through step 2, never by address. */
    @GetMapping("/reset")
    public String resetNotByAddress() {
        return "redirect:/forgot-password";
    }

    @PostMapping("/reset")
    public String reset(@Valid @ModelAttribute("resetPasswordRequest") ResetPasswordRequest request,
                        BindingResult bindingResult) {
        if (!bindingResult.hasErrors()) {
            try {
                service.resetPassword(request.getEmail(), request.getTicket(), request.getNewPassword(),
                        request.getConfirmPassword());
                return "redirect:/login?passwordReset";
            } catch (BadRequestException ex) {
                if ("ticket".equals(ex.getField())) {
                    return "redirect:" + verifyUrl(request.getEmail(), "expired");
                }
                String field = switch (String.valueOf(ex.getField())) {
                    case "confirmPassword", "newPassword" -> ex.getField();
                    default -> "email";
                };
                bindingResult.rejectValue(field, "invalid", ex.getMessage());
            }
        } else if (bindingResult.hasFieldErrors("ticket") || bindingResult.hasFieldErrors("email")) {
            return "redirect:/forgot-password";
        }
        request.setNewPassword(null);
        request.setConfirmPassword(null);
        return "auth/reset-password";
    }

    private static String verifyUrl(String email, String flag) {
        return UriComponentsBuilder.fromPath("/forgot-password/verify")
                .queryParam("email", PasswordResetService.normalize(email))
                .queryParam(flag, "").encode().build().toUriString();
    }
}
