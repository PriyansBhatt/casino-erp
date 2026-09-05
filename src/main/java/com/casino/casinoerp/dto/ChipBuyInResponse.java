package com.casino.casinoerp.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Map;

public record ChipBuyInResponse(
        UUID id,
        String buyInCode,
        UUID customerId,
        UUID customerSessionId,
        BigDecimal amountReceived,
        String paymentMode,
        BigDecimal totalChipValueIssued,
        Map<Integer, Long> denominations,
        String paymentReference,
        LocalDate businessDate,
        LocalDateTime createdAt,
        ActorReferenceResponse createdBy,
        String remarks
) {
}
