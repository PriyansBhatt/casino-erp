package com.casino.casinoerp.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ChipCashOutResponse(
        UUID id,
        String cashOutCode,
        UUID customerId,
        UUID customerSessionId,
        BigDecimal cashPaid,
        BigDecimal totalChipValueReturned,
        String paymentMode,
        String paymentReference,
        LocalDate businessDate,
        LocalDateTime createdAt,
        ActorReferenceResponse createdBy,
        String remarks
) {
}
