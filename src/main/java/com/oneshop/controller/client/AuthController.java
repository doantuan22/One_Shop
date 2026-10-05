package com.oneshop.controller.client;

import com.oneshop.dto.request.LoginRequest;
import com.oneshop.dto.request.RegisterRequest;
import com.oneshop.dto.response.AuthResponse;
import com.oneshop.exception.BadRequestException;
import com.oneshop.security.jwt.JwtCookieService;
import com.oneshop.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/** Login/registration pages. The JWT is delivered to the browser as an HttpOnly cookie. */
@Controller
public class AuthController {

    private final AuthService authService;
    private final JwtCookieService cookieService;

    public AuthController(AuthService authService, JwtCookieService cookieService) {
        this.authService = authService;
        this.cookieService = cookieService;
    }

    @GetMapping("/login")
    public String loginForm(Model model) {
        model.addAttribute("loginRequest", new LoginRequest());
        return "auth/login";
    }

    @PostMapping("/login")
    public String login(@Valid @ModelAttribute("loginRequest") LoginRequest request, BindingResult bindingResult,
                        Model model, HttpServletResponse response) {
        if (bindingResult.hasErrors()) {
            return "auth/login";
        }
        try {
            AuthResponse auth = authService.login(request);
            cookieService.addTokenCookie(response, auth.accessToken(), auth.expiresInSeconds());
            return "redirect:" + homeOf(auth);
        } catch (AuthenticationException ex) {
            model.addAttribute("loginError", "Email hoặc mật khẩu không đúng");
            return "auth/login";
        }
    }

    /** Landing page of each role. Only a convenience: access to these areas is decided by SecurityConfig. */
    private static String homeOf(AuthResponse auth) {
        if (auth.roles().contains("ROLE_ADMIN")) {
            return "/admin";
        }
        return auth.roles().contains("ROLE_STAFF") ? "/staff" : "/";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("registerRequest", new RegisterRequest());
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("registerRequest") RegisterRequest request,
                           BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "auth/register";
        }
        try {
            authService.register(request);
        } catch (BadRequestException ex) {
            bindingResult.rejectValue("email", "email.taken", ex.getMessage());
            return "auth/register";
        }
        return "redirect:/login?registered";
    }
}
