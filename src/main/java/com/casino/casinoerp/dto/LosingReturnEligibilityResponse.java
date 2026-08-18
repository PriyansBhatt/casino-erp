package com.casino.casinoerp.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record LosingReturnEligibilityResponse(
        UUID customerId, UUID customerSessionId, LocalDate businessDate,
        BigDecimal totalBuyIn, BigDecimal verifiedWins, BigDecimal verifiedLosses,
        BigDecimal previousCashOuts, BigDecimal previousLosingReturns,
        BigDecimal eligibleVerifiedLoss, BigDecimal minimumEligibleLoss,
        BigDecimal returnRate, BigDecimal availableReturnAmount,
        boolean eligible, String eligibilityReason
) {}
