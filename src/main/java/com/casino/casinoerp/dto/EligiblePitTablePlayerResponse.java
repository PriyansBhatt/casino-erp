package com.casino.casinoerp.dto;

import com.casino.casinoerp.entity.CustomerStatus;
import java.util.UUID;

public record EligiblePitTablePlayerResponse(
        UUID customerId,
        String customerCode,
        String customerName,
        CustomerStatus customerStatus,
        UUID customerSessionId,
        String sessionCode,
        String badge,
        boolean eligible,
        String ineligibilityReason) {
}
