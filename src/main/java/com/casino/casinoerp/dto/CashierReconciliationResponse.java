package com.casino.casinoerp.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record CashierReconciliationResponse(
        UUID id,
        LocalDate businessDate,
        String cashierUsername,
        String cashierName,
        String status,
        String lifecycleStatus,
        BigDecimal openingCash,
        BigDecimal physicalCashReceived,
        BigDecimal physicalCashPaid,
        BigDecimal expectedClosingCash,
        BigDecimal actualClosingCash,
        BigDecimal variance,
        Map<String, TenderSummaryResponse> buyInTenders,
        Map<String, TenderSummaryResponse> cashOutTenders,
        Map<String, TenderSummaryResponse> losingReturnTenders,
        Map<Integer, Integer> denominations,
        LocalDateTime submittedAt,
        String remarks,
        LocalDateTime reopenedAt,
        String reopenReason,
        String calculationBasis
) {}
