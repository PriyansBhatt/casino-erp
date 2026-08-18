package com.casino.casinoerp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReopenCashierReconciliationRequest(
        @NotBlank @Size(max = 1000) String reason
) {}
