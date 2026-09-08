package com.casino.casinoerp;

import com.casino.casinoerp.dto.BusinessDateHealthResponse;
import com.casino.casinoerp.dto.CreateBusinessDateContinuationOverrideRequest;
import com.casino.casinoerp.dto.RevokeBusinessDateContinuationOverrideRequest;
import com.casino.casinoerp.entity.BusinessDateContinuationOverride;
import com.casino.casinoerp.entity.BusinessDateHealth;
import com.casino.casinoerp.entity.User;
import com.casino.casinoerp.exception.ResourceConflictException;
import com.casino.casinoerp.repository.AuditLogRepository;
import com.casino.casinoerp.repository.BusinessDateContinuationOverrideRepository;
import com.casino.casinoerp.repository.BusinessDateRepository;
import com.casino.casinoerp.repository.UserRepository;
import com.casino.casinoerp.service.AuthenticatedUserService;
import com.casino.casinoerp.service.BusinessDateContinuationOverrideService;
import com.casino.casinoerp.service.BusinessDateService;
import com.casino.casinoerp.service.RolePermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BusinessDateContinuationOverrideServiceTests {
    private static final LocalDate DATE = LocalDate.of(2026, 9, 2);
    private static final Instant NOW = Instant.parse("2026-09-09T07:15:00Z");

    private final BusinessDateContinuationOverrideRepository overrides = mock(BusinessDateContinuationOverrideRepository.class);
    private final BusinessDateRepository businessDates = mock(BusinessDateRepository.class);
    private final BusinessDateService dates = mock(BusinessDateService.class);
    private final AuthenticatedUserService authenticatedUsers = mock(AuthenticatedUserService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AuditLogRepository audits = mock(AuditLogRepository.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private BusinessDateContinuationOverrideService service;

    @BeforeEach
    void setUp() {
        service = new BusinessDateContinuationOverrideService(overrides, businessDates, dates,
                authenticatedUsers, new RolePermissionService(), users, audits, clock);
        when(dates.getHealth()).thenReturn(stale(7));
        when(dates.currentCasinoDateTime()).thenReturn(LocalDateTime.of(2026, 9, 9, 13, 0));
        when(dates.getStaleOperationGraceEnd()).thenReturn(LocalTime.of(12, 30));
        when(overrides.findByBusinessDateAndRevokedAtIsNull(DATE)).thenReturn(Optional.empty());
        when(overrides.saveAndFlush(any())).thenAnswer(invocation -> {
            BusinessDateContinuationOverride value = invocation.getArgument(0);
            if (value.getId() == null) value.setId(UUID.randomUUID());
            return value;
        });
    }

    @Test
    void directorCanCreateAndBackendCalculatesExpiry() {
        user("DIRECTOR");
        var result = service.create(new CreateBusinessDateContinuationOverrideRequest("Delayed settlement", 30));
        assertThat(result.businessDate()).isEqualTo(DATE);
        assertThat(result.expiresAt()).isEqualTo(NOW.plusSeconds(1800));
        assertThat(result.active()).isTrue();
        verify(businessDates).acquireLifecycleLock();
        verify(audits).save(any());
    }

    @Test
    void superAdminCanCreate() {
        user("SUPER_ADMIN");
        assertThat(service.create(new CreateBusinessDateContinuationOverrideRequest("Emergency continuation", 60)).active())
                .isTrue();
    }

    @Test
    void ordinaryUserCannotCreateOrRevoke() {
        user("CASHIER");
        assertThatThrownBy(() -> service.create(new CreateBusinessDateContinuationOverrideRequest("No", 10)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.revoke(new RevokeBusinessDateContinuationOverrideRequest("No")))
                .isInstanceOf(AccessDeniedException.class);
        verify(businessDates, never()).acquireLifecycleLock();
    }

    @Test
    void healthyMissingAndInconsistentCannotCreate() {
        user("DIRECTOR");
        for (BusinessDateHealth health : new BusinessDateHealth[] {
                BusinessDateHealth.HEALTHY, BusinessDateHealth.MISSING, BusinessDateHealth.INCONSISTENT }) {
            when(dates.getHealth()).thenReturn(new BusinessDateHealthResponse(
                    health == BusinessDateHealth.MISSING || health == BusinessDateHealth.INCONSISTENT ? null : DATE,
                    DATE, health, false, 0, null));
            assertThatThrownBy(() -> service.create(
                    new CreateBusinessDateContinuationOverrideRequest("Invalid state", 10)))
                    .isInstanceOf(ResourceConflictException.class);
        }
    }

    @Test
    void staleWithinGraceCannotCreate() {
        user("DIRECTOR");
        when(dates.getHealth()).thenReturn(stale(1));
        when(dates.currentCasinoDateTime()).thenReturn(LocalDateTime.of(2026, 9, 3, 12, 30));
        assertThatThrownBy(() -> service.create(new CreateBusinessDateContinuationOverrideRequest("Too early", 10)))
                .isInstanceOf(ResourceConflictException.class).hasMessageContaining("grace");
    }

    @Test
    void reasonAndDurationAreServerValidated() {
        user("DIRECTOR");
        assertThatThrownBy(() -> service.create(new CreateBusinessDateContinuationOverrideRequest(" ", 10)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(new CreateBusinessDateContinuationOverrideRequest("Reason", 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(new CreateBusinessDateContinuationOverrideRequest("Reason", 61)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void duplicateActiveOverrideConflicts() {
        user("DIRECTOR");
        when(overrides.findByBusinessDateAndRevokedAtIsNull(DATE)).thenReturn(Optional.of(value(NOW.plusSeconds(600))));
        assertThatThrownBy(() -> service.create(new CreateBusinessDateContinuationOverrideRequest("Again", 10)))
                .isInstanceOf(ResourceConflictException.class).hasMessageContaining("already exists");
    }

    @Test
    void expiredOverrideIsIneffectiveAndMayBeSuperseded() {
        user("DIRECTOR");
        BusinessDateContinuationOverride expired = value(NOW);
        when(overrides.findByBusinessDateAndRevokedAtIsNull(DATE)).thenReturn(Optional.of(expired));
        var result = service.create(new CreateBusinessDateContinuationOverrideRequest("Replacement", 10));
        assertThat(expired.getRevokedAt()).isEqualTo(NOW);
        assertThat(expired.getRevokeReason()).isEqualTo("SYSTEM_EXPIRED_REPLACEMENT");
        assertThat(result.active()).isTrue();
        verify(audits, org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    void revokeImmediatelyMakesOverrideInactiveAndAudits() {
        User actor = user("SUPER_ADMIN");
        BusinessDateContinuationOverride active = value(NOW.plusSeconds(600));
        when(overrides.findByBusinessDateAndRevokedAtIsNull(DATE)).thenReturn(Optional.of(active));
        var result = service.revoke(new RevokeBusinessDateContinuationOverrideRequest("Risk resolved"));
        assertThat(result.active()).isFalse();
        assertThat(result.terminationType()).isEqualTo("REVOKED");
        assertThat(result.revokedBy().id()).isEqualTo(actor.getId());
        assertThat(active.getRevokeReason()).isEqualTo("Risk resolved");
        verify(audits).save(any());
    }

    @Test
    void overrideForAnotherDateDoesNotApply() {
        when(dates.getHealth()).thenReturn(stale(7));
        assertThat(service.activeFor(DATE.plusDays(1))).isEmpty();
    }

    @Test
    void expiryBoundaryIsStrictAndDeterministic() {
        BusinessDateContinuationOverride value = value(NOW);
        when(overrides.findByBusinessDateAndRevokedAtIsNull(DATE)).thenReturn(Optional.of(value));

        assertThat(serviceAt(NOW.minusSeconds(1)).activeFor(DATE)).contains(value);
        assertThat(serviceAt(NOW).activeFor(DATE)).isEmpty();
        assertThat(serviceAt(NOW.plusSeconds(1)).activeFor(DATE)).isEmpty();
    }

    private BusinessDateContinuationOverrideService serviceAt(Instant instant) {
        return new BusinessDateContinuationOverrideService(overrides, businessDates, dates,
                authenticatedUsers, new RolePermissionService(), users, audits,
                Clock.fixed(instant, ZoneOffset.UTC));
    }

    private User user(String role) {
        User user = new User();
        user.setId(UUID.randomUUID()); user.setUsername(role.toLowerCase()); user.setFullName(role); user.setRole(role);
        when(authenticatedUsers.getRequiredUser()).thenReturn(user);
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        return user;
    }

    private BusinessDateContinuationOverride value(Instant expiresAt) {
        BusinessDateContinuationOverride value = new BusinessDateContinuationOverride();
        value.setId(UUID.randomUUID()); value.setBusinessDate(DATE); value.setReason("Reason");
        value.setAuthorizedBy(UUID.randomUUID()); value.setAuthorizedAt(NOW.minusSeconds(60)); value.setExpiresAt(expiresAt);
        return value;
    }

    private BusinessDateHealthResponse stale(long days) {
        return new BusinessDateHealthResponse(DATE, DATE.plusDays(days), BusinessDateHealth.STALE, true, days, "stale");
    }
}
