package com.casino.casinoerp.dto;

import com.casino.casinoerp.entity.RosterStatus;
import java.time.*;
import java.util.UUID;

public record StaffRosterResponse(UUID id,StaffReference staff,ShiftDefinitionResponse shift,
        LocalDate rosterDate,LocalDateTime scheduledStart,LocalDateTime scheduledEnd,
        RosterStatus status,String remarks,String cancellationReason,UUID cancelledBy,
        LocalDateTime cancelledAt,LocalDateTime createdAt,LocalDateTime updatedAt) {
    public record StaffReference(UUID staffProfileId,UUID userId,String employeeCode,String username,String fullName) {}
}
