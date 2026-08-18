package com.casino.casinoerp.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record CreateLosingReturnRequest(
        @NotNull UUID customerId,
        @NotNull UUID customerSessionId,
        @NotBlank @Size(max=100) String idempotencyKey,
        @Size(max=1000) String remarks
) {}
