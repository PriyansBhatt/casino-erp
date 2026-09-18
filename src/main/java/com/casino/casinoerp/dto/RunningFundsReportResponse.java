package com.casino.casinoerp.dto;

import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

/** Selected-date aggregates; never a physical-cash balance or custody snapshot. */
public record RunningFundsReportResponse(
        LocalDate businessDate, String status, OffsetDateTime windowStart, OffsetDateTime windowEnd,
        long sessionEntries, long distinctGuestsEntered,
        Map<String, Tender> buyIn, Map<String, Tender> cashOut,
        long losingReturnCount, @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal losingReturnAmountPaid,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal customerWins, @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal customerLosses,
        long submittedCount, long reopenedCount, @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal aggregateSubmittedVariance) {
    public record Tender(long count, @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount) {}
}
