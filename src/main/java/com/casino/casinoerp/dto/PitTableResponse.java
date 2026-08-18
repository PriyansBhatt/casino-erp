package com.casino.casinoerp.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record PitTableResponse(
        UUID id,
        String tableCode,
        String tableName,
        String gameType,
        String status,
        LocalDate businessDate,
        LocalDateTime openedAt,
        LocalDateTime closedAt,
        BigDecimal openingFloat,
        BigDecimal closingFloat,
        String remarks
) {}
