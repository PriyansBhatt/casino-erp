package com.casino.casinoerp.dto;

import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

public record LosingReturnResponse(
        UUID id, String losingReturnCode, UUID customerId, UUID customerSessionId,
        LocalDate businessDate, BigDecimal eligibleVerifiedLoss, BigDecimal returnRate,
        BigDecimal amountPaid, String paymentMode, LocalDateTime createdAt, String remarks
) {}
