package com.casino.casinoerp.service;

import com.casino.casinoerp.entity.User;
import com.casino.casinoerp.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CurrentUserRoleService {

    private final UserRepository userRepository;

    public CurrentUserRoleService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public String getCurrentUserRole() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication.getAuthorities().isEmpty()) {
            return "UNKNOWN";
        }

        return authentication.getAuthorities()
                .iterator()
                .next()
                .getAuthority()
                .replace("ROLE_", "");
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