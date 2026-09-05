package com.casino.casinoerp.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Map;

public record ChipCashOutResponse(
        UUID id,
        String cashOutCode,
        UUID customerId,
        UUID customerSessionId,
        BigDecimal cashPaid,
        BigDecimal totalChipValueReturned,
        Map<Integer, Long> denominations,
        String paymentMode,
        String paymentReference,
        LocalDate businessDate,
        LocalDateTime createdAt,
        ActorReferenceResponse createdBy,
        String remarks
) {
}
