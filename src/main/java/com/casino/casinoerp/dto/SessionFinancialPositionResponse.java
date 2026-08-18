package com.casino.casinoerp.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SessionFinancialPositionResponse(
        UUID customerId,
        UUID customerSessionId,
        LocalDate businessDate,
        BigDecimal totalBuyIn,
        BigDecimal totalCashOut,
        BigDecimal verifiedGamingWin,
        BigDecimal verifiedGamingLoss,
        BigDecimal calculatedChipPosition
) {
}
