package com.casino.casinoerp;

import com.casino.casinoerp.security.Role;
import com.casino.casinoerp.service.*;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SystemLockServiceTests {
    private final CurrentUserRoleService roles = mock(CurrentUserRoleService.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final UUID actorId = UUID.randomUUID();

    @Test
    void emergencyUnlockOverridesScheduledWindowUntilLockEndAndIsAudited() {
        Clock clock = Clock.fixed(
                ZonedDateTime.of(2026, 8, 20, 8, 0, 0, 0, ZoneId.of("Asia/Kathmandu")).toInstant(),
                ZoneId.of("Asia/Kathmandu"));
        when(roles.getCurrentRole()).thenReturn(Optional.of(Role.SUPER_ADMIN));
        when(roles.getCurrentUserId()).thenReturn(actorId);
        SystemLockService service = new SystemLockService(
                roles, new RolePermissionService(), audit, clock);

        assertThat(service.isSystemLocked()).isTrue();
        service.unlockSystem("Opening cage inventory");

        assertThat(service.isSystemLocked()).isFalse();
        assertThat(service.isEmergencyUnlockActive()).isTrue();
        assertThat(service.getEmergencyUnlockUntil()).isEqualTo(
                LocalDateTime.of(2026, 8, 20, 12, 30));
        assertThat(service.getEmergencyUnlockedBy()).isEqualTo(actorId);
        assertThat(service.getEmergencyUnlockReason()).isEqualTo("Opening cage inventory");
        verify(audit).log(eq("SYSTEM_UNLOCK"), eq("SYSTEM_LOCK"), isNull(), eq(actorId),
                contains("Opening cage inventory"));
    }

    @Test
    void relockClearsEmergencyOverrideAndBlankReasonIsRejected() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-20T03:00:00Z"), ZoneId.of("Asia/Kathmandu"));
        when(roles.getCurrentRole()).thenReturn(Optional.of(Role.SUPER_ADMIN));
        when(roles.getCurrentUserId()).thenReturn(actorId);
        SystemLockService service = new SystemLockService(
                roles, new RolePermissionService(), audit, clock);

        assertThatThrownBy(() -> service.unlockSystem(" "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("reason");
        service.unlockSystem("Authorized maintenance");
        service.lockSystem();

        assertThat(service.isSystemLocked()).isTrue();
        assertThat(service.isEmergencyUnlockActive()).isFalse();
        assertThat(service.getEmergencyUnlockedBy()).isNull();
        assertThat(service.getEmergencyUnlockReason()).isNull();
    }
}
