package com.oneshop.service.impl;

import com.oneshop.dto.request.LoginRequest;
import com.oneshop.dto.request.RegisterRequest;
import com.oneshop.dto.response.AuthResponse;
import com.oneshop.dto.response.UserResponse;
import com.oneshop.entity.Role;
import com.oneshop.entity.RoleName;
import com.oneshop.entity.User;
import com.oneshop.exception.BadRequestException;
import com.oneshop.repository.RoleRepository;
import com.oneshop.repository.UserRepository;
import com.oneshop.security.jwt.JwtService;
import com.oneshop.service.AuthService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class AuthServiceImpl implements AuthService {

    private static final String TOKEN_TYPE = "Bearer";
    /** Same rule as the DTO validation, re-checked here so every caller of the Service is covered. */
    private static final Pattern USER_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_.]{4,30}$");

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthServiceImpl(AuthenticationManager authenticationManager, JwtService jwtService,
                           UserRepository userRepository, RoleRepository roleRepository,
                           PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        // The identifier is an e-mail or a user_name; CustomUserDetailsService decides by the presence of '@'.
        // An unknown identifier and a wrong password both end in the same BadCredentialsException.
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getIdentifier().trim(), request.getPassword()));
        UserDetails principal = (UserDetails) authentication.getPrincipal();
        return new AuthResponse(
                jwtService.generateToken(principal),
                TOKEN_TYPE,
                jwtService.getExpirationSeconds(),
                principal.getUsername(),
                principal.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
    }

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalize(request.getEmail());
        String userName = request.getUserName() == null ? "" : request.getUserName().trim();
        if (!USER_NAME_PATTERN.matcher(userName).matches()) {
            throw new BadRequestException("userName",
                    "Tên đăng nhập từ 4 đến 30 ký tự, chỉ gồm chữ, số, dấu _ và dấu chấm, không chứa @");
        }
        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("email", "Email đã được sử dụng");
        }
        if (userRepository.existsByUserNameIgnoreCase(userName)) {
            throw new BadRequestException("userName", "Tên đăng nhập đã được sử dụng");
        }
        // roles is a fixed lookup table seeded by database/04_seed_data.sql (CHECK limits it to 3 names)
        Role customerRole = roleRepository.findByName(RoleName.CUSTOMER)
                .orElseThrow(() -> new IllegalStateException("Role CUSTOMER is missing: run the database scripts"));

        User user = new User();
        user.setEmail(email);
        user.setUserName(userName);
        user.setFullName(request.getFullName().trim());
        user.setPhone(request.getPhone() == null || request.getPhone().isBlank() ? null : request.getPhone().trim());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(customerRole);
        User saved;
        try {
            saved = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            // Two concurrent registrations passed the exists* checks: the unique constraints decide.
            throw new BadRequestException("Email hoặc tên đăng nhập đã được sử dụng");
        }
        return new UserResponse(saved.getId(), saved.getEmail(), saved.getUserName(), saved.getFullName());
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
