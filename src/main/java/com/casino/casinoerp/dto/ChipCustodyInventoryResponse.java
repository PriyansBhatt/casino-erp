package com.casino.casinoerp.dto;

import com.casino.casinoerp.entity.ChipCustodyLocationType;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record ChipCustodyInventoryResponse(
        ChipCustodyLocationType locationType,
        UUID referenceId,
        boolean initialized,
        Map<Integer, Long> denominations,
        BigDecimal totalValue
) {
}
