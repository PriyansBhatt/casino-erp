package com.casino.casinoerp.dto;

import com.casino.casinoerp.entity.BusinessDateHealth;

import java.time.LocalDate;

public record BusinessDateHealthResponse(
        LocalDate businessDate,
        LocalDate expectedBusinessDate,
        BusinessDateHealth health,
        boolean stale,
        long staleByDays,
        String lifecycleWarning) {
}
