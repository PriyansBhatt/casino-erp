package com.casino.casinoerp.service;

import org.springframework.stereotype.Service;

@Service
public class RolePermissionService {

    public boolean canCreateSession(String roleName) {
        return isSuperAdmin(roleName)
                || "Receptionist".equalsIgnoreCase(roleName);
    }

    public boolean canBuyIn(String roleName) {
        return isSuperAdmin(roleName)
                || "Cashier".equalsIgnoreCase(roleName);
    }

    public boolean canCashOut(String roleName) {
        return isSuperAdmin(roleName)
                || "Cashier".equalsIgnoreCase(roleName);
    }

    public boolean canPitTransaction(String roleName) {
        return isSuperAdmin(roleName)
                || "Pit Supervisor".equalsIgnoreCase(roleName)
                || "Dealer".equalsIgnoreCase(roleName);
    }

    public boolean canCloseBusinessDate(String roleName) {
        return isSuperAdmin(roleName)
                || "Manager".equalsIgnoreCase(roleName);
    }

    public boolean canViewAuditLogs(String roleName) {
        return isSuperAdmin(roleName)
                || "Compliance Officer".equalsIgnoreCase(roleName)
                || "Surveillance Officer".equalsIgnoreCase(roleName);
    }

    private boolean isSuperAdmin(String roleName) {
        return "Super Admin".equalsIgnoreCase(roleName)
                || "SUPER_ADMIN".equalsIgnoreCase(roleName);
    }
}