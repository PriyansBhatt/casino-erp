package com.casino.casinoerp.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public interface CustomerVisitSummaryProjection {
    UUID getCustomerId();

    long getTotalVisits();

    LocalDate getLastVisitBusinessDate();

    LocalDateTime getLastEntryTime();

    boolean getHasActiveSession();

    UUID getActiveSessionId();
}
