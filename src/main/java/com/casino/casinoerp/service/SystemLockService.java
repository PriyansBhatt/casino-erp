package com.casino.casinoerp.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Service
public class SystemLockService {

    private static final LocalTime LOCK_START =
            LocalTime.of(6, 30);

    private static final LocalTime LOCK_END =
            LocalTime.of(12, 30);

    private final CurrentUserRoleService currentUserRoleService;
    private final RolePermissionService rolePermissionService;
    private final AuditLogService auditLogService;
    private final Clock clock;
    private boolean systemLocked = false;
    private LocalDateTime emergencyUnlockUntil;
    private UUID emergencyUnlockedBy;
    private String emergencyUnlockReason;

    @Autowired
    public SystemLockService(CurrentUserRoleService currentUserRoleService,
                             RolePermissionService rolePermissionService,
                             AuditLogService auditLogService) {
        this(currentUserRoleService, rolePermissionService, auditLogService, Clock.systemDefaultZone());
    }

    public SystemLockService(CurrentUserRoleService currentUserRoleService,
                             RolePermissionService rolePermissionService,
                             AuditLogService auditLogService,
                             Clock clock) {
        this.currentUserRoleService = currentUserRoleService;
        this.rolePermissionService = rolePermissionService;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    public boolean isSystemLocked() {
        LocalDateTime now = LocalDateTime.now(clock);

        if (isEmergencyUnlockActive(now)) {
            return false;
        }

        boolean timeLocked =
                !now.toLocalTime().isBefore(LOCK_START)
                        && !now.toLocalTime().isAfter(LOCK_END);

        return systemLocked || timeLocked;
    }

    public void lockSystem() {
        validatePermission("Only Super Admin can lock system.");

        this.systemLocked = true;
        clearEmergencyUnlock();

        auditLogService.log(
                "SYSTEM_LOCK",
                "SYSTEM_LOCK",
                null,
                currentUserRoleService.getCurrentUserId(),
                "System locked"
        );
    }

    public void unlockSystem(String reason) {
        validatePermission("Only Super Admin can unlock system.");

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Emergency unlock reason is required.");
        }

        this.systemLocked = false;
        LocalDateTime now = LocalDateTime.now(clock);
        this.emergencyUnlockUntil = emergencyUnlockExpiry(now);
        this.emergencyUnlockedBy = currentUserRoleService.getCurrentUserId();
        this.emergencyUnlockReason = reason.trim();

        auditLogService.log(
                "SYSTEM_UNLOCK",
                "SYSTEM_LOCK",
                null,
                emergencyUnlockedBy,
                "Emergency system unlock: " + emergencyUnlockReason
        );
    }

    public LocalTime getLockStart() { return LOCK_START; }
    public LocalTime getLockEnd() { return LOCK_END; }
    public boolean isEmergencyUnlockActive() { return isEmergencyUnlockActive(LocalDateTime.now(clock)); }
    public LocalDateTime getEmergencyUnlockUntil() { return emergencyUnlockUntil; }
    public UUID getEmergencyUnlockedBy() { return emergencyUnlockedBy; }
    public String getEmergencyUnlockReason() { return emergencyUnlockReason; }
    public boolean isManuallyLocked() { return systemLocked; }
    public boolean isScheduledLockActive() { return isScheduledLockWindow(LocalTime.now(clock)); }

    private boolean isEmergencyUnlockActive(LocalDateTime now) {
        if (emergencyUnlockUntil == null) return false;
        if (now.isBefore(emergencyUnlockUntil)) return true;
        clearEmergencyUnlock();
        return false;
    }

    private boolean isScheduledLockWindow(LocalTime now) {
        return !now.isBefore(LOCK_START) && !now.isAfter(LOCK_END);
    }

    private LocalDateTime emergencyUnlockExpiry(LocalDateTime now) {
        if (isScheduledLockWindow(now.toLocalTime())) {
            return LocalDateTime.of(now.toLocalDate(), LOCK_END);
        }
        if (now.toLocalTime().isBefore(LOCK_START)) {
            return LocalDateTime.of(now.toLocalDate(), LOCK_START);
        }
        return LocalDateTime.of(now.toLocalDate().plusDays(1), LOCK_START);
    }

    private void clearEmergencyUnlock() {
        emergencyUnlockUntil = null;
        emergencyUnlockedBy = null;
        emergencyUnlockReason = null;
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
