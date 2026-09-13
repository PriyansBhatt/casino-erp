package com.casino.casinoerp.dto;

import com.casino.casinoerp.entity.PaymentMode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.Map;

public record CreateChipBuyInRequest(
        @NotNull(message = "Customer ID is required") UUID customerId,
        @NotNull(message = "Customer session ID is required") UUID customerSessionId,
        @NotNull(message = "Amount received is required")
        @DecimalMin(value = "0.01", message = "Amount received must be greater than 0")
        BigDecimal amountReceived,
        @NotNull(message = "Payment mode is required") PaymentMode paymentMode,
        @NotNull(message = "Total chip value issued is required")
        @DecimalMin(value = "0.01", message = "Total chip value issued must be greater than 0")
        BigDecimal totalChipValueIssued,
        @NotNull(message = "Chip denominations are required")
        @JsonDeserialize(contentUsing = StrictBuyInQuantityDeserializer.class)
        Map<Integer, Long> denominations,
        @Size(max = 150, message = "Payment reference must not exceed 150 characters")
        String paymentReference,
        @Size(max = 500, message = "Remarks must not exceed 500 characters") String remarks,
        @NotBlank(message = "Idempotency key is required")
        @Size(max = 100, message = "Idempotency key must not exceed 100 characters")
        String idempotencyKey
) {
}
