package com.casino.casinoerp.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record OperationalStatusResponse(
        LocalDate businessDate,
        boolean businessDateOpen,
        boolean systemLocked,
        String lockReason,
        LocalDateTime serverTimestamp) {
}
