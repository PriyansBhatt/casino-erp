package com.casino.casinoerp.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssignPitTableCustomerRequest(
        @NotNull(message = "Customer ID is required") UUID customerId,
        @NotNull(message = "Customer session ID is required") UUID customerSessionId
) {}
