package com.casino.casinoerp.service;

import com.casino.casinoerp.entity.User;
import com.casino.casinoerp.repository.UserRepository;
import com.casino.casinoerp.security.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.Optional;

@Service
public class CurrentUserRoleService {

    private final UserRepository userRepository;

    public CurrentUserRoleService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public String getCurrentUserRole() {
        return getCurrentRole()
                .map(Role::name)
                .orElse("UNKNOWN");
    }

    public Optional<Role> getCurrentRole() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication.getAuthorities().isEmpty()) {
            return Optional.empty();
        }

        return authentication.getAuthorities()
                .iterator()
                .next()
                .getAuthority()
                .transform(Role::fromAuthority);
    }

    public UUID getCurrentUserId() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication.getName() == null) {
            return null;
        }

        User user = userRepository.findByUsername(authentication.getName());

        if (user == null) {
            return null;
        }

        return user.getId();
    }

    public void setCurrentUserRole(String role) {
        // Not needed anymore. Role comes from JWT.
    }
}
