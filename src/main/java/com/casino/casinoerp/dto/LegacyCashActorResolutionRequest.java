package com.casino.casinoerp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LegacyCashActorResolutionRequest(
        @NotBlank(message = "Resolution reason is required")
        @Size(min = 20, max = 500, message = "Resolution reason must be between 20 and 500 characters")
        String reason,
        @NotBlank(message = "Idempotency key is required")
        @Size(max = 100, message = "Idempotency key must not exceed 100 characters")
        String idempotencyKey) {
}
