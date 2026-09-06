package com.casino.casinoerp.dto;

import com.casino.casinoerp.entity.PitTableStaffAssignmentRole;

import java.util.UUID;

public record PitStaffCandidateResponse(
        UUID id,
        String username,
        String fullName,
        PitTableStaffAssignmentRole role,
        String status) {
}
