package com.casino.casinoerp.service;

import com.casino.casinoerp.security.Role;
import org.springframework.stereotype.Service;

import java.util.function.Predicate;

@Service
public class RolePermissionService {

    public boolean canLookupReceptionCustomers(Role role) {
        return role == Role.RECEPTIONIST
                || role == Role.DIRECTOR
                || role == Role.SUPER_ADMIN;
    }

    public boolean canCreateSession(String roleName) {
        return withRole(roleName, this::canCreateSession);
    }

    public boolean canCreateSession(Role role) {
        return isSuperAdmin(role) || role == Role.RECEPTIONIST;
    }

    public boolean canBuyIn(String roleName) {
        return withRole(roleName, this::canBuyIn);
    }

    public boolean canBuyIn(Role role) {
        return isSuperAdmin(role) || role == Role.CASHIER;
    }

    public boolean canViewCashierTransactions(String roleName) { return withRole(roleName, this::canViewCashierTransactions); }
    public boolean canViewCashierTransactions(Role role) { return isSuperAdmin(role) || role == Role.CASHIER || role == Role.DIRECTOR; }

    public boolean canCashOut(String roleName) {
        return withRole(roleName, this::canCashOut);
    }

    public boolean canCashOut(Role role) {
        return isSuperAdmin(role) || role == Role.CASHIER;
    }

    public boolean canLosingReturn(String roleName) { return withRole(roleName, this::canLosingReturn); }
    public boolean canLosingReturn(Role role) { return isSuperAdmin(role) || role == Role.CASHIER; }
    public boolean canViewLosingReturn(String roleName) { return withRole(roleName, this::canViewLosingReturn); }
    public boolean canViewLosingReturn(Role role) { return canLosingReturn(role) || role == Role.DIRECTOR; }

    public boolean canPitTransaction(String roleName) {
        return withRole(roleName, this::canPitTransaction);
    }

    public boolean canPitTransaction(Role role) {
        return isSuperAdmin(role)
                || role == Role.PIT_SUPERVISOR
                || role == Role.DEALER;
    }

    public boolean canRecordVerifiedGamingResult(Role role) {
        return canPitTransaction(role);
    }

    public boolean canViewChipControl(Role role) {
        return role == Role.CASHIER
                || role == Role.PIT_SUPERVISOR
                || role == Role.DIRECTOR
                || role == Role.SUPER_ADMIN;
    }

    public boolean canViewChipCustody(Role role) {
        return role == Role.CASHIER || role == Role.PIT_SUPERVISOR
                || role == Role.DEALER || role == Role.DIRECTOR || role == Role.SUPER_ADMIN;
    }

    public boolean canViewChipCustodyHistory(Role role) {
        return role == Role.DIRECTOR || role == Role.SUPER_ADMIN;
    }

    public boolean canManageTableChipCustody(Role role) {
        return role == Role.PIT_SUPERVISOR || role == Role.SUPER_ADMIN;
    }

    public boolean canOpenPitTableOperation(Role role) {
        return role == Role.PIT_SUPERVISOR || role == Role.SUPER_ADMIN;
    }

    public boolean canManagePitTableStaff(Role role) {
        return role == Role.PIT_SUPERVISOR || role == Role.SUPER_ADMIN;
    }

    public boolean canViewPitTableStaff(Role role) {
        return role == Role.DEALER || canManagePitTableStaff(role);
    }

    public boolean canInitializeChipCustody(Role role) {
        return role == Role.SUPER_ADMIN;
    }

    public boolean canCorrectLegacyChipCustody(Role role) {
        return role == Role.SUPER_ADMIN;
    }

    public boolean canResolveLegacyPitTableReconciliation(Role role) {
        return role == Role.SUPER_ADMIN;
    }

    public boolean canResolveLegacyCashActorBucket(Role role) {
        return role == Role.SUPER_ADMIN;
    }

    public boolean canViewCashierReconciliation(Role role) {
        return role == Role.CASHIER || role == Role.DIRECTOR || role == Role.SUPER_ADMIN;
    }

    public boolean canSubmitCashierReconciliation(Role role) {
        return role == Role.CASHIER || role == Role.SUPER_ADMIN;
    }

    public boolean canReopenCashierReconciliation(Role role) {
        return role == Role.DIRECTOR || role == Role.SUPER_ADMIN;
    }

    public boolean canViewRunningFundsReport(Role role) {
        return role == Role.DIRECTOR || role == Role.SUPER_ADMIN;
    }

    public boolean canManageCustomerBonus(Role role) {
        return role == Role.DIRECTOR || role == Role.SUPER_ADMIN;
    }

    public boolean canManageHotelBooking(Role role) {
        return role == Role.DIRECTOR || role == Role.SUPER_ADMIN;
    }

    public boolean canManageBusinessDate(String roleName) {
        return withRole(roleName, this::canManageBusinessDate);
    }

    public boolean canManageBusinessDate(Role role) {
        return role == Role.SUPER_ADMIN || role == Role.DIRECTOR;
    }

    public boolean canViewAuditLogs(String roleName) {
        return withRole(roleName, this::canViewAuditLogs);
    }

    public boolean canViewAuditLogs(Role role) {
        return isSuperAdmin(role)
                || role == Role.COMPLIANCE_OFFICER
                || role == Role.SURVEILLANCE_OFFICER;
    }

    public boolean canUseSystemLock(Role role) {
        return isSuperAdmin(role);
    }

    private boolean isSuperAdmin(Role role) {
        return role == Role.SUPER_ADMIN;
    }

    private boolean withRole(String roleName, Predicate<Role> permission) {
        return Role.fromValue(roleName)
                .map(permission::test)
                .orElse(false);
    }
}
