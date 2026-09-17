package com.casino.casinoerp.controller;

import com.casino.casinoerp.dto.*;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import com.casino.casinoerp.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.success("User created", userService.create(request));
    }
    @PatchMapping("/{id}/role")
    public ApiResponse<UserResponse> role(@PathVariable UUID id, @Valid @RequestBody ChangeUserRoleRequest request) {
        return ApiResponse.success("User role changed", userService.changeRole(id, request));
    }
    @PatchMapping("/{id}/status")
    public ApiResponse<UserResponse> status(@PathVariable UUID id, @Valid @RequestBody ChangeUserStatusRequest request) {
        return ApiResponse.success("User status changed", userService.changeStatus(id, request));
    }
    @PostMapping("/{id}/password")
    public ApiResponse<UserResponse> password(@PathVariable UUID id, @Valid @RequestBody ResetUserPasswordRequest request) {
        return ApiResponse.success("Password reset; existing JWTs remain valid", userService.resetPassword(id, request));
    }

    @GetMapping
    public List<UserResponse> getAllUsers() {
        return userService.getAllUsers();
    }
}
