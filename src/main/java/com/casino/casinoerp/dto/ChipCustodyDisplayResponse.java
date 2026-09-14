package com.casino.casinoerp.dto;

/** Safe, optional display fields; movement UUIDs remain the audit identity. */
public record ChipCustodyDisplayResponse(String customerCode, String customerName, String sessionCode,
        String tableCode, String tableName, String actorUsername, String actorDisplayName) {}
