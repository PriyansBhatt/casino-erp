package com.casino.casinoerp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

public record LegacySessionCustodyCorrectionRequest(
        @NotNull(message = "Customer session ID is required") UUID customerSessionId,
        @NotEmpty(message = "Denominations are required") Map<Integer, Long> denominations,
        @NotBlank(message = "Correction reason is required")
        @Size(min = 10, max = 500,
                message = "Correction reason must be between 10 and 500 characters") String reason,
        @NotBlank(message = "Idempotency key is required")
        @Size(max = 100, message = "Idempotency key must not exceed 100 characters") String idempotencyKey
) {
}
