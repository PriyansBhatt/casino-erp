package com.casino.casinoerp.dto;
import jakarta.validation.constraints.*;
public record CreateUserRequest(@NotBlank @Size(max=100) String username,
        @NotBlank @Size(max=150) String fullName, @Size(max=150) String email,
        @NotBlank String role, String status,
        @com.fasterxml.jackson.annotation.JsonProperty(access=com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
        String password) {
    @Override public String toString() { return "CreateUserRequest[redacted]"; }
}
