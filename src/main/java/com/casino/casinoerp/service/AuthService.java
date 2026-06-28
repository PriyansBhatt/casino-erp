package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.LoginRequest;
import com.casino.casinoerp.dto.LoginResponse;
import com.casino.casinoerp.entity.User;
import com.casino.casinoerp.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final CurrentUserRoleService currentUserRoleService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            UserRepository userRepository,
            CurrentUserRoleService currentUserRoleService,
            JwtService jwtService,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.currentUserRoleService = currentUserRoleService;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    public LoginResponse login(LoginRequest request) {

        User user = userRepository.findByUsername(request.getUsername());

        if (user == null) {
            return new LoginResponse("Invalid username", null, null, null, null);        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            return new LoginResponse("Invalid password", null, null, null, null);        }

        currentUserRoleService.setCurrentUserRole(user.getRole());

        String token = jwtService.generateToken(user.getUsername(), user.getRole());

        return new LoginResponse(
                "Login successful",
                user.getUsername(),
                user.getStatus(),
                user.getRole(),
                token
        );
    }
}