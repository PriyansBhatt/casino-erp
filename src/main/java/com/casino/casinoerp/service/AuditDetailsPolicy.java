package com.casino.casinoerp.service;

import java.util.Set;

public final class AuditDetailsPolicy {
    private AuditDetailsPolicy() {}
    // Reviewed current writers: fixed text, validated enums/numbers/UUIDs, generated codes.
    // No free-form reasons, staff-entered codes, legacy corrections or unknown events.
    // Account transitions include unrestricted legacy before-values: withhold them too.
    private static final Set<String> SAFE = Set.of(
        "USER_CREATED", "USER_PASSWORD_RESET",
        "OPEN_BUSINESS_DATE", "CLOSE_BUSINESS_DATE", "REOPEN_BUSINESS_DATE", "SYSTEM_LOCK",
        "CREATE_SESSION", "CLOSE_SESSION", "CHECK_IN", "CREATE_WALLET", "CREATE_SERVICE_RECORD",
        "CREATE_BUY_IN", "CREATE_CASH_OUT", "CREATE_LOSING_RETURN", "CREATE_VERIFIED_GAMING_RESULT",
        "CREATE_CHIP_CUSTODY_MOVEMENT", "CREATE_PIT_TRANSACTION",
        "ASSIGN_PIT_TABLE_CUSTOMER", "LEAVE_PIT_TABLE_CUSTOMER",
        "CREATE_FNB_REQUEST", "CHANGE_FNB_STATUS", "CREATE_CRM_RECORD", "CHANGE_CRM_STATUS",
        "START_SLOT_PLAY", "END_SLOT_PLAY", "CHANGE_MACHINE_STATUS",
        "CASHIER_OPENING_BALANCE_CREATED", "RECONCILIATION_SUBMITTED");
    public static boolean allows(String action) { return action != null && SAFE.contains(action); }
}
