package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.PitTableStaffAssignmentRole;

import java.time.LocalDateTime;
import java.util.UUID;

public interface PitTableActiveStaffProjection {
    UUID getPitTableId();
    UUID getAssignmentId();
    UUID getUserId();
    String getUsername();
    String getDisplayName();
    PitTableStaffAssignmentRole getAssignmentRole();
    LocalDateTime getStartedAt();
}
