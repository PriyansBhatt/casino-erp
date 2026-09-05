package com.casino.casinoerp.dto;

import com.casino.casinoerp.entity.PitTableCustomerAssignmentStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.UUID;

public record PitTablePlayerResponse(
        UUID assignmentId,
        UUID pitTableId,
        UUID customerId,
        String customerCode,
        String customerName,
        UUID customerSessionId,
        String sessionCode,
        String badge,
        LocalDate businessDate,
        LocalDateTime joinedAt,
        PitTableCustomerAssignmentStatus status,
        LocalDateTime leftAt,
        BigDecimal calculatedChipPosition
) {}
