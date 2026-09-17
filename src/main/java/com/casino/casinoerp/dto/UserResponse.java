package com.casino.casinoerp.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public class UserResponse {

    private final UUID id;
    private final String username;
    private final String fullName;
    private final String email;
    private final String status;
    private final String role;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private StaffLink staff;
    public record StaffLink(UUID id, String employeeCode) {}

    public UserResponse(UUID id, String username, String fullName, String email, String status,
                        String role, LocalDateTime createdAt, LocalDateTime updatedAt, StaffLink staff) {
        this(id, username, fullName, email, status, role, createdAt);
        this.updatedAt = updatedAt;
        this.staff = staff;
    }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public StaffLink getStaff() { return staff; }


    public UserResponse(
            UUID id,
            String username,
            String fullName,
            String email,
            String status,
            String role,
            LocalDateTime createdAt
    ) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.email = email;
        this.status = status;
        this.role = role;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getStatus() { return status; }
    public String getRole() { return role; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
