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

    public boolean canCashOut(String roleName) {
        return withRole(roleName, this::canCashOut);
    }

    public boolean canCashOut(Role role) {
        return isSuperAdmin(role) || role == Role.CASHIER;
    }

    public boolean canPitTransaction(String roleName) {
        return withRole(roleName, this::canPitTransaction);
    }

    public boolean canPitTransaction(Role role) {
        return isSuperAdmin(role)
                || role == Role.PIT_SUPERVISOR
                || role == Role.DEALER;
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
