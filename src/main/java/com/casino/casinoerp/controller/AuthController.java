package com.casino.casinoerp.controller;

import com.casino.casinoerp.dto.ApiResponse;
import com.casino.casinoerp.dto.LoginRequest;
import com.casino.casinoerp.dto.LoginResponse;
import com.casino.casinoerp.service.AuthService;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);

        if (!"Login successful".equals(response.getMessage())) {
            return ApiResponse.error(response.getMessage());
        }

        return ApiResponse.success("Login successful", response);
    }
}