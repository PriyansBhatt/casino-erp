package com.casino.casinoerp.dto;

import com.casino.casinoerp.entity.BusinessDateHealth;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Instant;

public record OperationalStatusResponse(
        LocalDate businessDate,
        LocalDate expectedBusinessDate,
        boolean businessDateOpen,
        BusinessDateHealth businessDateHealth,
        boolean businessDateStale,
        long staleByDays,
        String lifecycleWarning,
        boolean systemLocked,
        String lockReason,
        boolean continuationOverrideActive,
        Instant continuationOverrideExpiresAt,
        String continuationOverrideReason,
        BusinessDateContinuationOverrideResponse.Actor continuationOverrideAuthorizedBy,
        LocalDateTime serverTimestamp) {
    public OperationalStatusResponse(LocalDate businessDate, LocalDate expectedBusinessDate,
            boolean businessDateOpen, BusinessDateHealth businessDateHealth, boolean businessDateStale,
            long staleByDays, String lifecycleWarning, boolean systemLocked, String lockReason,
            LocalDateTime serverTimestamp) {
        this(businessDate, expectedBusinessDate, businessDateOpen, businessDateHealth, businessDateStale,
                staleByDays, lifecycleWarning, systemLocked, lockReason, false, null, null, null,
                serverTimestamp);
    }
}
