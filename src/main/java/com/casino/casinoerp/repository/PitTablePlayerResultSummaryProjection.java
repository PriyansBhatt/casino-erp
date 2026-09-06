package com.casino.casinoerp.repository;

import java.math.BigDecimal;
import java.util.UUID;

public interface PitTablePlayerResultSummaryProjection {
    UUID getAssignmentId();
    BigDecimal getVerifiedWinTotal();
    BigDecimal getVerifiedLossTotal();
}
