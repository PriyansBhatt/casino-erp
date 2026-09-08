package com.casino.casinoerp.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BusinessDateContinuationOverrideResponse(
        UUID id,
        LocalDate businessDate,
        String reason,
        Actor actor,
        Instant authorizedAt,
        Instant expiresAt,
        boolean active,
        boolean expired,
        String terminationType,
        Instant revokedAt,
        Actor revokedBy,
        String revokeReason) {
    public BusinessDateContinuationOverrideResponse(UUID id, LocalDate businessDate, String reason,
            Actor actor, Instant authorizedAt, Instant expiresAt, boolean active, Instant revokedAt,
            Actor revokedBy, String revokeReason) {
        this(id, businessDate, reason, actor, authorizedAt, expiresAt, active,
                !active && revokedAt == null, revokedAt == null ? null : "REVOKED",
                revokedAt, revokedBy, revokeReason);
    }

    public record Actor(UUID id, String username, String fullName) { }
}
