package com.casino.casinoerp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Map;

public record CashierReconciliationRequest(
        @Deprecated BigDecimal openingCash,
        @NotNull
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(contentUsing = StrictCashCountQuantityDeserializer.class)
        Map<Integer, @NotNull @jakarta.validation.constraints.PositiveOrZero Integer> denominations,
        @Size(max = 1000) String remarks,
        @NotBlank @Size(max = 100) String idempotencyKey,
        @NotNull java.time.LocalDate expectedBusinessDate
) {}
