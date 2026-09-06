package com.casino.casinoerp.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record LegacyPitTableReconciliationRequest(
        @NotNull(message = "Physical closing float is required.")
        @DecimalMin(value = "0", message = "Physical closing float must be zero or greater.")
        BigDecimal physicalClosingFloat,
        @NotBlank(message = "A meaningful legacy resolution reason is required.")
        @Size(min = 20, max = 500, message = "Legacy resolution reason must be between 20 and 500 characters.")
        String reason,
        @NotBlank(message = "Idempotency key is required.")
        @Size(max = 100, message = "Idempotency key must not exceed 100 characters.")
        String idempotencyKey) {
}
