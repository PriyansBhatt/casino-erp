package com.casino.casinoerp.dto;

import com.casino.casinoerp.entity.VerifiedGamingResultType;
import com.casino.casinoerp.entity.VerifiedGamingSourceType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Map;

public record VerifiedGamingResultResponse(
        UUID id,
        UUID customerId,
        UUID customerSessionId,
        UUID pitTableId,
        UUID assignmentId,
        LocalDate businessDate,
        VerifiedGamingSourceType sourceType,
        VerifiedGamingResultType resultType,
        Map<Integer, Integer> denominations,
        BigDecimal amount,
        LocalDateTime createdAt,
        ActorReferenceResponse createdBy
) {
}
