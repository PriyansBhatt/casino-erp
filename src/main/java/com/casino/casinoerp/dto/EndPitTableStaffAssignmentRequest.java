package com.casino.casinoerp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EndPitTableStaffAssignmentRequest(
        @Size(max = 1000) String remarks,
        @NotBlank @Size(max = 100) String idempotencyKey) {
}
