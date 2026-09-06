package com.casino.casinoerp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmergencyUnlockRequest(
        @NotBlank(message = "Emergency unlock reason is required")
        @Size(max = 500, message = "Emergency unlock reason must not exceed 500 characters")
        String reason
) {
}
