package com.casino.casinoerp.repository;

import java.math.BigDecimal;
import java.util.UUID;

public interface PitTableCustodySummaryProjection {
    UUID getPitTableId();
    BigDecimal getCustodyTotal();
}
