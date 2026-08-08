package com.casino.casinoerp.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record OpenCustomerSessionRequest(
        @NotNull(message = "Customer ID is required") UUID customerId
) {
}
