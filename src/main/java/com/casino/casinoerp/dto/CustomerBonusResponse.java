package com.casino.casinoerp.dto;

import com.casino.casinoerp.entity.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

public record CustomerBonusResponse(
        UUID id,
        String bonusCode,
        UUID customerId,
        String customerCode,
        String customerName,
        UUID customerSessionId,
        String sessionCode,
        LocalDate businessDate,
        CustomerBonusType bonusType,
        BigDecimal amount,
        String reason,
        CustomerBonusStatus status,
        ActorReferenceResponse createdBy,
        ActorReferenceResponse approvedBy,
        LocalDateTime createdAt,
        LocalDateTime approvedAt
) {}
