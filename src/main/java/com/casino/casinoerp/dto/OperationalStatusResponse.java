package com.casino.casinoerp.dto;

import com.casino.casinoerp.entity.BusinessDateHealth;

import java.time.LocalDate;
import java.time.LocalDateTime;

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
        LocalDateTime serverTimestamp) {
}
