package com.oneshop.security.service;

import com.oneshop.entity.User;
import com.oneshop.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * The login identifier is an e-mail address or a user_name: a value containing '@' is an e-mail
     * (a user_name can never contain '@'), anything else is a user_name. The role is fetched eagerly, so no
     * extra transaction is needed beyond the repository call itself.
     */
    @Override
    public AppUserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        String value = identifier == null ? "" : identifier.trim();
        var found = value.indexOf('@') >= 0
                ? userRepository.findByEmail(value.toLowerCase(Locale.ROOT))
                : userRepository.findByUserNameIgnoreCase(value);
        return toDetails(found.orElseThrow(() -> new UsernameNotFoundException("User not found")));
    }

    /** Used by the JWT filter: the token subject is the immutable user_id. */
    public AppUserDetails loadUserById(Long userId) throws UsernameNotFoundException {
        return toDetails(userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found")));
    }

    private static AppUserDetails toDetails(User user) {
        return new AppUserDetails(user.getId(), user.getEmail(), user.getPasswordHash(), user.isActive(),
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().getName().name())));
    }
}
