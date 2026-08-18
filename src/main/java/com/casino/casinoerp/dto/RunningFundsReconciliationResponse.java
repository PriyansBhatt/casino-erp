package com.casino.casinoerp.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record RunningFundsReconciliationResponse(
        UUID reconciliationId,
        String cashierUsername,
        String cashierName,
        BigDecimal cashReceived,
        BigDecimal cashPaid,
        BigDecimal expectedClosingCash,
        BigDecimal actualClosingCash,
        BigDecimal variance,
        String status,
        String lifecycleStatus
) {}
