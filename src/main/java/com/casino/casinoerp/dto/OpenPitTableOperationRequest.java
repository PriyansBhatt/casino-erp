package com.casino.casinoerp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record OpenPitTableOperationRequest(
        @NotEmpty(message = "Opening denominations are required") Map<Integer, Long> denominations,
        @Size(max = 500, message = "Remarks must not exceed 500 characters") String remarks,
        @NotBlank(message = "Idempotency key is required")
        @Size(max = 100, message = "Idempotency key must not exceed 100 characters") String idempotencyKey) {
}
