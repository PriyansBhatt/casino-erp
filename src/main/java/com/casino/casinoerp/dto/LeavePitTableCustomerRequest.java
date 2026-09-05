package com.casino.casinoerp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record LeavePitTableCustomerRequest(
        @NotNull(message = "Returned denominations are required")
        Map<Integer, Long> denominations,
        @NotBlank(message = "Settlement idempotency key is required")
        @Size(max = 100, message = "Settlement idempotency key must not exceed 100 characters")
        String idempotencyKey
) {
}
