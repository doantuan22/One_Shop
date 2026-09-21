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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Service
public class AuthServiceImpl implements AuthService {

    private static final String TOKEN_TYPE = "Bearer";

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
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalize(request.getEmail()), request.getPassword()));
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
        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("Email đã được sử dụng");
        }
        Role customerRole = roleRepository.findByName(RoleName.ROLE_CUSTOMER)
                .orElseGet(() -> roleRepository.save(new Role(RoleName.ROLE_CUSTOMER)));

        User user = new User();
        user.setEmail(email);
        user.setFullName(request.getFullName().trim());
        user.setPhone(request.getPhone() == null || request.getPhone().isBlank() ? null : request.getPhone().trim());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRoles(new HashSet<>(Set.of(customerRole)));
        User saved = userRepository.save(user);
        return new UserResponse(saved.getId(), saved.getEmail(), saved.getFullName());
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
