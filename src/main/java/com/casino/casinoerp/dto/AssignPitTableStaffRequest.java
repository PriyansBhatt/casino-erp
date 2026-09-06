package com.casino.casinoerp.dto;

import com.casino.casinoerp.entity.PitTableStaffAssignmentRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AssignPitTableStaffRequest(
        @NotNull UUID staffUserId,
        @NotNull PitTableStaffAssignmentRole assignmentRole,
        @Size(max = 1000) String remarks,
        @NotBlank @Size(max = 100) String idempotencyKey) {
}
