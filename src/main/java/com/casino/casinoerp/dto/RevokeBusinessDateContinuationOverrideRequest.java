package com.casino.casinoerp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RevokeBusinessDateContinuationOverrideRequest(
        @NotBlank(message = "Revocation reason is required")
        @Size(max = 500, message = "Revocation reason must not exceed 500 characters")
        String reason) {
}
