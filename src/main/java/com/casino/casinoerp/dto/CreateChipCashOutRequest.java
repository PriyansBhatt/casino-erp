package com.casino.casinoerp.dto;

import com.casino.casinoerp.entity.PaymentMode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.Map;

public record CreateChipCashOutRequest(
        @NotNull(message = "Customer ID is required") UUID customerId,
        @NotNull(message = "Customer session ID is required") UUID customerSessionId,
        @NotNull(message = "Cash paid is required")
        @DecimalMin(value = "0.01", message = "Cash paid must be greater than 0")
        BigDecimal cashPaid,
        @NotNull(message = "Total chip value returned is required")
        @DecimalMin(value = "0.01", message = "Total chip value returned must be greater than 0")
        BigDecimal totalChipValueReturned,
        @NotNull(message = "Chip denominations are required")
        @JsonDeserialize(contentUsing = StrictBuyInQuantityDeserializer.class)
        Map<Integer, @NotNull @jakarta.validation.constraints.PositiveOrZero Long> denominations,
        @NotNull(message = "Payment mode is required") PaymentMode paymentMode,
        @Size(max = 150, message = "Payment reference must not exceed 150 characters")
        String paymentReference,
        @Size(max = 500, message = "Remarks must not exceed 500 characters") String remarks,
        @NotBlank(message = "Idempotency key is required")
        @Size(max = 100, message = "Idempotency key must not exceed 100 characters")
        String idempotencyKey
) {
}
