package com.casino.casinoerp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record HandoverPitTableStaffRequest(
        @NotNull UUID newStaffUserId,
        @Size(max = 1000) String remarks,
        @NotBlank @Size(max = 100) String idempotencyKey) {
}
