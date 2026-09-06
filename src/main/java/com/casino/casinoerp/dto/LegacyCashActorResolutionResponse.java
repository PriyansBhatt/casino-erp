package com.casino.casinoerp.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record LegacyCashActorResolutionResponse(
        UUID id,
        UUID actorUserId,
        String actorUsername,
        LocalDate businessDate,
        BigDecimal cashReceived,
        BigDecimal cashPaid,
        BigDecimal netCashMovement,
        String openingCashVerification,
        String status,
        BigDecimal expectedClosing,
        BigDecimal variance,
        String reason,
        UUID resolvedBy,
        LocalDateTime resolvedAt) {
}
