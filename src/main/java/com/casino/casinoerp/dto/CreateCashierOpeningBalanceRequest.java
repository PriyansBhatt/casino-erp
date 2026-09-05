package com.casino.casinoerp.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateCashierOpeningBalanceRequest(
        @NotNull @DecimalMin("0.00") BigDecimal openingCashAmount
) {}
