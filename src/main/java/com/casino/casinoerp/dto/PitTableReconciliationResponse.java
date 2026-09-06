package com.casino.casinoerp.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PitTableReconciliationResponse(
        UUID tableId,
        String tableCode,
        BigDecimal openingFloat,
        BigDecimal closingFloat,
        BigDecimal tableDifference,
        String tableStatus,
        String openingFloatVerification,
        BigDecimal legacyOpeningFloatGap,
        String legacyResolutionReason,
        UUID legacyResolvedBy,
        LocalDateTime legacyResolvedAt,
        String idempotencyKey) {
}
