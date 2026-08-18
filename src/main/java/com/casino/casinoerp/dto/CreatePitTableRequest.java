package com.casino.casinoerp.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreatePitTableRequest(
        @NotBlank @Size(max = 50) String tableCode,
        @NotBlank @Size(max = 100) String tableName,
        @NotBlank @Size(max = 50) String gameType,
        @DecimalMin(value = "0.00") BigDecimal openingFloat,
        @Size(max = 500) String remarks
) {}
