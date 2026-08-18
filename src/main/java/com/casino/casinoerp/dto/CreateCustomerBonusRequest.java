package com.casino.casinoerp.dto;

import com.casino.casinoerp.entity.CustomerBonusType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateCustomerBonusRequest(
        @NotNull UUID customerId,
        @NotNull UUID customerSessionId,
        @NotNull CustomerBonusType bonusType,
        @NotNull @DecimalMin(value = "0.01", message = "Bonus amount must be greater than zero") BigDecimal amount,
        @NotBlank @Size(max = 1000) String reason,
        @NotBlank @Size(max = 100) String idempotencyKey
) {}
