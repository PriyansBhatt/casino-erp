package com.casino.casinoerp.service;

import org.springframework.stereotype.Service;

import java.time.LocalTime;

@Service
public class SystemLockService {

    private static final LocalTime LOCK_START =
            LocalTime.of(6, 30);

    private static final LocalTime LOCK_END =
            LocalTime.of(12, 30);

    private final CurrentUserRoleService currentUserRoleService;
    private final RolePermissionService rolePermissionService;
    private final AuditLogService auditLogService;
    private boolean systemLocked = false;

    public SystemLockService(CurrentUserRoleService currentUserRoleService,
                             RolePermissionService rolePermissionService,
                             AuditLogService auditLogService) {
        this.currentUserRoleService = currentUserRoleService;
        this.rolePermissionService = rolePermissionService;
        this.auditLogService = auditLogService;
    }

    public boolean isSystemLocked() {
        LocalTime now = LocalTime.now();

        boolean timeLocked =
                !now.isBefore(LOCK_START)
                        && !now.isAfter(LOCK_END);

        return systemLocked || timeLocked;
    }

    public void lockSystem() {
        validatePermission("Only Super Admin can lock system.");

        this.systemLocked = true;

        auditLogService.log(
                "SYSTEM_LOCK",
                "SYSTEM_LOCK",
                null,
                currentUserRoleService.getCurrentUserId(),
                "System locked"
        );
    }

    public void unlockSystem() {
        validatePermission("Only Super Admin can unlock system.");

        this.systemLocked = false;

        auditLogService.log(
                "SYSTEM_UNLOCK",
                "SYSTEM_LOCK",
                null,
                currentUserRoleService.getCurrentUserId(),
                "System unlocked"
        );
    }


    public void validateLockUnlockPermission() {
        validatePermission("Only Super Admin can lock/unlock system.");
    }

    private void validatePermission(String message) {
        if (!currentUserRoleService.getCurrentRole()
                .map(rolePermissionService::canUseSystemLock)
                .orElse(false)) {
            throw new RuntimeException(message);
        }
    }
}
