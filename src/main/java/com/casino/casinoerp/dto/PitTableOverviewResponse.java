package com.casino.casinoerp.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PitTableOverviewResponse(
        UUID physicalTableId,
        String tableCode,
        String tableName,
        String gameType,
        Integer maxPlayers,
        String physicalStatus,
        UUID operationId,
        LocalDate businessDate,
        String status,
        BigDecimal openingFloat,
        long currentPlayers,
        BigDecimal chipIn,
        BigDecimal verifiedWins,
        BigDecimal verifiedLosses,
        BigDecimal netPosition) {
}
