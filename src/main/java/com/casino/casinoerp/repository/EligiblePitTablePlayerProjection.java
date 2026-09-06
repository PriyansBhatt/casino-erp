package com.casino.casinoerp.repository;

import java.util.UUID;

public interface EligiblePitTablePlayerProjection {
    UUID getCustomerId();
    String getCustomerCode();
    String getCustomerName();
    String getCustomerStatus();
    UUID getCustomerSessionId();
    String getSessionCode();
    UUID getActiveAssignmentId();
}
