package com.casino.casinoerp.dto;

import com.casino.casinoerp.entity.PitTableStaffAssignmentRole;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record PitTableStaffAssignmentResponse(
        UUID assignmentId,
        UUID pitTableId,
        UUID staffUserId,
        String username,
        String fullName,
        PitTableStaffAssignmentRole assignmentRole,
        LocalDate businessDate,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        ActorReferenceResponse assignedBy,
        ActorReferenceResponse endedBy,
        String remarks,
        String endRemarks,
        boolean active) {
}
