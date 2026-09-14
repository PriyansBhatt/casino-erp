package com.casino.casinoerp.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Persisted payout snapshot; never reconstructed from current eligibility. */
public record LosingReturnHistoryResponse(
        UUID id, String losingReturnCode, UUID customerId, String customerCode, String customerName,
        UUID customerSessionId, String sessionCode, LocalDate businessDate,
        BigDecimal eligibleVerifiedLoss, BigDecimal returnRate, BigDecimal amountPaid,
        String paymentMode, LocalDateTime createdAt, UUID actorId, String actorUsername, String remarks
) {}
