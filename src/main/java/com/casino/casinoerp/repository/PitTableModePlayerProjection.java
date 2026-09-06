package com.casino.casinoerp.repository;

import java.time.LocalDateTime;
import java.util.UUID;

public interface PitTableModePlayerProjection {
    UUID getAssignmentId();
    UUID getCustomerId();
    String getCustomerCode();
    String getCustomerName();
    UUID getCustomerSessionId();
    String getSessionCode();
    LocalDateTime getJoinedAt();
}
