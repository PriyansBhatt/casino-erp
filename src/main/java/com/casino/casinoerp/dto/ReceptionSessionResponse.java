package com.casino.casinoerp.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ReceptionSessionResponse(
        UUID id,
        String sessionCode,
        UUID customerId,
        LocalDate sessionDate,
        LocalDateTime entryTime,
        LocalDateTime exitTime,
        String status,
        UUID openedBy,
        UUID closedBy,
        LocalDate businessDate,
        String remarks
) {
}
