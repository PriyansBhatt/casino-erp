package com.casino.casinoerp.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record AuditLogResponse(UUID id, LocalDate businessDate, String actionType,
        String moduleName, UUID entityId, LocalDateTime performedAt, Actor actor,
        String safeDetails, boolean detailsWithheld) {
    // Names are current directory labels, never historical role/name snapshots.
    public record Actor(UUID id, String username, String fullName) {}
}
