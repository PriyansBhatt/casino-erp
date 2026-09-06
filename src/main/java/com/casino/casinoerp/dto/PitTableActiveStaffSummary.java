package com.casino.casinoerp.dto;

import com.casino.casinoerp.entity.PitTableStaffAssignmentRole;

import java.time.LocalDateTime;
import java.util.UUID;

public record PitTableActiveStaffSummary(
        UUID assignmentId,
        UUID userId,
        String username,
        String displayName,
        PitTableStaffAssignmentRole assignmentRole,
        LocalDateTime startedAt) {
}
