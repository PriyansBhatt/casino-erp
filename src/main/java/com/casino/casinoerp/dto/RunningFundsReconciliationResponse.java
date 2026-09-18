package com.casino.casinoerp.dto;

import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Saved record values; reopened closing/result fields are unavailable. Labels come from the current directory; no free-text remarks. */
public record RunningFundsReconciliationResponse(
        UUID id, LocalDate businessDate, UUID cashierId, String cashierUsername, String cashierName,
        String lifecycleStatus, String status, String calculationBasis,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal openingCash, @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal expectedClosingCash, @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal actualClosingCash,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal variance, LocalDateTime submittedAt, LocalDateTime reopenedAt) {}
