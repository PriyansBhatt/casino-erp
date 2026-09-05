package com.casino.casinoerp.dto;

import com.casino.casinoerp.entity.ChipCustodyLocationType;
import com.casino.casinoerp.entity.ChipCustodyMovementType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record ChipCustodyMovementResponse(
        UUID id,
        ChipCustodyMovementType movementType,
        LocalDate businessDate,
        ChipCustodyLocationType sourceType,
        UUID sourceReferenceId,
        ChipCustodyLocationType destinationType,
        UUID destinationReferenceId,
        String relatedTransactionType,
        UUID relatedTransactionId,
        UUID customerSessionId,
        UUID pitTableId,
        Map<Integer, Long> denominations,
        BigDecimal totalValue,
        UUID createdBy,
        LocalDateTime createdAt
) {
}
