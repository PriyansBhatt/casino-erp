package com.casino.casinoerp.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record RunningFundsReportResponse(
        LocalDate businessDate,
        String businessDateStatus,
        LocalDateTime generatedAt,
        BigDecimal buyInReceived,
        BigDecimal cashOutPaid,
        BigDecimal losingReturnPaid,
        BigDecimal netCustomerCashMovement,
        BigDecimal verifiedGamingWins,
        BigDecimal verifiedGamingLosses,
        BigDecimal casinoGamingNet,
        BigDecimal outstandingCustomerChipPosition,
        int submittedCashiers,
        int reopenedCashiers,
        int unresolvedCashiers,
        BigDecimal aggregateSubmittedVariance,
        List<RunningFundsReconciliationResponse> reconciliations
) {}
