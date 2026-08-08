package com.casino.casinoerp;

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
}
