package com.casino.casinoerp.dto;

import com.casino.casinoerp.entity.VerifiedGamingResultType;
import com.casino.casinoerp.entity.VerifiedGamingSourceType;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record CreateVerifiedGamingResultRequest(
        @NotNull(message = "Customer ID is required") UUID customerId,
        @NotNull(message = "Customer session ID is required") UUID customerSessionId,
        @NotNull(message = "Pit table ID is required") UUID pitTableId,
        @NotNull(message = "Pit table assignment ID is required") UUID assignmentId,
        @NotNull(message = "Source type is required") VerifiedGamingSourceType sourceType,
        @NotNull(message = "Result type is required") VerifiedGamingResultType resultType,
        @NotNull(message = "Denominations are required") @com.fasterxml.jackson.databind.annotation.JsonDeserialize(contentUsing = StrictCashCountQuantityDeserializer.class)
        Map<Integer, @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.PositiveOrZero Integer> denominations,
        BigDecimal amount,
        @jakarta.validation.constraints.NotBlank(message = "Idempotency key is required")
        @jakarta.validation.constraints.Size(max = 100, message = "Idempotency key must not exceed 100 characters")
        String idempotencyKey
) {
}
