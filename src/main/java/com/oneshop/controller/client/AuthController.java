package com.oneshop.controller.client;

import com.oneshop.dto.request.LoginRequest;
import com.oneshop.dto.request.RegisterRequest;
import com.oneshop.dto.response.AuthResponse;
import com.oneshop.exception.BadRequestException;
import com.oneshop.exception.TooManyLoginAttemptsException;
import com.oneshop.security.ClientIp;
import com.oneshop.security.jwt.JwtCookieService;
import com.oneshop.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
                        Model model, HttpServletRequest http, HttpServletResponse response) {
        if (bindingResult.hasErrors()) {
            return "auth/login";
        }
        try {
            AuthResponse auth = authService.login(request, ClientIp.of(http));
            cookieService.addTokenCookie(response, auth.accessToken(), auth.expiresInSeconds());
            return "redirect:" + homeOf(auth);
        } catch (TooManyLoginAttemptsException ex) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()));
            model.addAttribute("loginError", ex.getMessage());
            return "auth/login";
        } catch (AuthenticationException ex) {
            model.addAttribute("loginError", "Sai tài khoản hoặc mật khẩu");
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
            String field = switch (String.valueOf(ex.getField())) {
                case "userName", "password" -> ex.getField();
                default -> "email";
            };
            bindingResult.rejectValue(field, field + ".taken", ex.getMessage());
            return "auth/register";
        }
        return "redirect:/login?registered";
    }
}
