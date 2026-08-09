package com.casino.casinoerp.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ReceptionCustomerResponse(
        UUID id,
        String customerCode,
        String fullName,
        String phone,
        String nationality,
        String status,
        long totalVisits,
        LocalDate lastVisitBusinessDate,
        LocalDateTime lastEntryTime,
        boolean hasActiveSession,
        UUID activeSessionId
) {
}
