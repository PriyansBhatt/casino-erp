package com.casino.casinoerp.service;

import com.casino.casinoerp.entity.User;
import com.casino.casinoerp.repository.UserRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuthenticatedUserService {

    private final UserRepository userRepository;

    public AuthenticatedUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getRequiredUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || authentication.getName() == null) {
            throw new IllegalStateException("An authenticated user is required.");
        }

        User user = userRepository.findByUsername(authentication.getName());
        if (user == null) {
            throw new IllegalStateException("Authenticated user account was not found.");
        }

        return user;
    }
}
