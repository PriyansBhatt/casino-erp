package com.casino.casinoerp.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ChipControlSessionResponse(
        UUID customerId,
        String customerCode,
        String customerName,
        UUID customerSessionId,
        String sessionCode,
        LocalDate businessDate,
        LocalDateTime entryTime,
        String sessionStatus,
        String badge,
        UUID activeTableId,
        String activeTableCode,
        String activeTableName,
        BigDecimal totalBuyIn,
        BigDecimal verifiedGamingWin,
        BigDecimal verifiedGamingLoss,
        BigDecimal totalCashOut,
        BigDecimal calculatedChipPosition,
        String exposureStatus
) {}
