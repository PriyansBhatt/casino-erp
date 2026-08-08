package com.casino.casinoerp;

import com.casino.casinoerp.security.Role;
import com.casino.casinoerp.service.RolePermissionService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RolePermissionServiceTests {

    private final RolePermissionService rolePermissionService = new RolePermissionService();

    @Test
    void superAdminCanManageBusinessDates() {
        assertThat(rolePermissionService.canManageBusinessDate("SUPER_ADMIN")).isTrue();
    }

    @Test
    void directorCanManageBusinessDates() {
        assertThat(rolePermissionService.canManageBusinessDate("DIRECTOR")).isTrue();
    }

    @Test
    void ordinaryAuthenticatedRoleCannotManageBusinessDates() {
        assertThat(rolePermissionService.canManageBusinessDate("CASHIER")).isFalse();
    }

    @Test
    void managerCannotManageBusinessDates() {
        assertThat(rolePermissionService.canManageBusinessDate("MANAGER")).isFalse();
    }

    @Test
    void accountManagerCannotManageBusinessDates() {
        assertThat(rolePermissionService.canManageBusinessDate("ACCOUNT_MANAGER")).isFalse();
    }

    @Test
    void directorCannotUseSystemLock() {
        assertThat(rolePermissionService.canUseSystemLock(Role.DIRECTOR)).isFalse();
    }

    @Test
    void superAdminCanUseSystemLock() {
        assertThat(rolePermissionService.canUseSystemLock(Role.SUPER_ADMIN)).isTrue();
    }

    @Test
    void canonicalPitSupervisorAndLegacyLabelKeepPitTransactionAccess() {
        assertThat(rolePermissionService.canPitTransaction("PIT_SUPERVISOR")).isTrue();
        assertThat(rolePermissionService.canPitTransaction("Pit Supervisor")).isTrue();
    }

    @Test
    void unknownRoleHasNoPermissions() {
        assertThat(rolePermissionService.canCreateSession("UNKNOWN_ROLE")).isFalse();
        assertThat(rolePermissionService.canBuyIn("UNKNOWN_ROLE")).isFalse();
        assertThat(rolePermissionService.canViewAuditLogs("UNKNOWN_ROLE")).isFalse();
    }
}
