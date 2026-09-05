package com.casino.casinoerp.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record CashierOpeningBalanceResponse(
        UUID id,
        UUID cashierUserId,
        String cashierUsername,
        String cashierName,
        LocalDate businessDate,
        BigDecimal openingCashAmount,
        UUID createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
