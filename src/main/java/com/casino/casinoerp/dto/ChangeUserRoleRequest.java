package com.casino.casinoerp.dto;
import jakarta.validation.constraints.*;
public record ChangeUserRoleRequest(@NotBlank String role, @NotBlank String expectedRole) {
    @Override public String toString() { return "ChangeUserRoleRequest[redacted]"; }
}
