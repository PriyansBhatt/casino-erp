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
    private final AuditLogService auditLogService;
    private boolean systemLocked = false;

    public SystemLockService(CurrentUserRoleService currentUserRoleService,
                             AuditLogService auditLogService) {
        this.currentUserRoleService = currentUserRoleService;
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
        String role = currentUserRoleService.getCurrentUserRole();

        if (!role.equalsIgnoreCase("Super Admin")
                && !role.equalsIgnoreCase("SUPER_ADMIN")) {
            throw new RuntimeException("Only Super Admin can lock system.");
        }

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
        String role = currentUserRoleService.getCurrentUserRole();

        if (!role.equalsIgnoreCase("Super Admin")
                && !role.equalsIgnoreCase("SUPER_ADMIN")) {
            throw new RuntimeException("Only Super Admin can unlock system.");
        }

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
        String role = currentUserRoleService.getCurrentUserRole();

        if (!role.equalsIgnoreCase("Super Admin")
                && !role.equalsIgnoreCase("SUPER_ADMIN")) {
            throw new RuntimeException(
                    "Only Super Admin can lock/unlock system."
            );
        }
    }
}