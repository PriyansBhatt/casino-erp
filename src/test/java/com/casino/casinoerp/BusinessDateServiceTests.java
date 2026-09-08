package com.casino.casinoerp;

import com.casino.casinoerp.entity.BusinessDate;
import com.casino.casinoerp.entity.BusinessDateHealth;
import com.casino.casinoerp.repository.BusinessDateRepository;
import com.casino.casinoerp.security.Role;
import com.casino.casinoerp.service.AuditLogService;
import com.casino.casinoerp.service.BusinessDateService;
import com.casino.casinoerp.service.BusinessDateValidationService;
import com.casino.casinoerp.service.CurrentUserRoleService;
import com.casino.casinoerp.service.RolePermissionService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BusinessDateServiceTests {
    private static final ZoneId CASINO_ZONE = ZoneId.of("Asia/Kathmandu");

    @Test
    void resolvesBeforeNineAmToPreviousCasinoBusinessDate() {
        BusinessDateRepository repository = mock(BusinessDateRepository.class);
        when(repository.findByStatus("OPEN")).thenReturn(List.of());
        BusinessDateService service = new BusinessDateService(repository,
                mock(BusinessDateValidationService.class), mock(AuditLogService.class),
                new RolePermissionService(), mock(CurrentUserRoleService.class), fixedClock());

        assertThat(service.resolveBusinessDate(LocalDateTime.of(2026, 9, 8, 3, 30)))
                .isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(service.resolveBusinessDate(LocalDateTime.of(2026, 9, 8, 9, 0)))
                .isEqualTo(LocalDate.of(2026, 9, 8));
    }

    @Test
    void operationalResolverStillUsesPersistedOpenBusinessDate() {
        BusinessDateRepository repository = mock(BusinessDateRepository.class);
        BusinessDate open = new BusinessDate();
        open.setBusinessDate(LocalDate.of(2026, 9, 2));
        open.setStatus("OPEN");
        when(repository.findByStatus("OPEN")).thenReturn(List.of(open));
        BusinessDateService service = service(repository);

        assertThat(service.resolveBusinessDate(LocalDateTime.of(2026, 9, 8, 0, 18)))
                .isEqualTo(LocalDate.of(2026, 9, 2));
    }

    @Test
    void staleOperationalOpenDateDoesNotOverrideAttendanceDate() {
        BusinessDateRepository repository = mock(BusinessDateRepository.class);
        BusinessDate open = new BusinessDate();
        open.setBusinessDate(LocalDate.of(2026, 9, 2));
        open.setStatus("OPEN");
        when(repository.findByStatus("OPEN")).thenReturn(List.of(open));
        BusinessDateService service = service(repository);

        assertThat(service.resolveAttendanceBusinessDate(
                at(2026, 9, 8, 0, 18)))
                .isEqualTo(LocalDate.of(2026, 9, 7));
        verify(repository, never()).findByStatus("OPEN");
    }

    @Test
    void attendanceDateDoesNotRequireOpenOperationalDate() {
        BusinessDateRepository repository = mock(BusinessDateRepository.class);
        BusinessDateService service = service(repository);

        assertThat(service.resolveAttendanceBusinessDate(
                at(2026, 9, 8, 0, 18)))
                .isEqualTo(LocalDate.of(2026, 9, 7));
    }

    @Test
    void attendanceDateHonorsNineAmBoundary() {
        BusinessDateService service = service(mock(BusinessDateRepository.class));

        assertThat(service.resolveAttendanceBusinessDate(
                ZonedDateTime.of(2026, 9, 8, 8, 59, 59, 0, CASINO_ZONE).toInstant()))
                .isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(service.resolveAttendanceBusinessDate(
                ZonedDateTime.of(2026, 9, 8, 9, 0, 0, 0, CASINO_ZONE).toInstant()))
                .isEqualTo(LocalDate.of(2026, 9, 8));
        assertThat(service.resolveAttendanceBusinessDate(
                at(2026, 9, 8, 18, 0)))
                .isEqualTo(LocalDate.of(2026, 9, 8));
    }

    @Test
    void closesBusinessDateAfterAuthoritativeRequirementsAreResolved() {
        LocalDate date = LocalDate.of(2026, 8, 8);
        BusinessDateRepository repository = mock(BusinessDateRepository.class);
        BusinessDateValidationService validation = mock(BusinessDateValidationService.class);
        AuditLogService audit = mock(AuditLogService.class);
        CurrentUserRoleService currentRole = mock(CurrentUserRoleService.class);
        BusinessDateService service = new BusinessDateService(repository, validation, audit,
                new RolePermissionService(), currentRole, fixedClock());
        BusinessDate businessDate = new BusinessDate();
        businessDate.setBusinessDate(date);
        businessDate.setStatus("OPEN");
        when(currentRole.getCurrentRole()).thenReturn(Optional.of(Role.SUPER_ADMIN));
        when(repository.findByBusinessDate(date)).thenReturn(Optional.of(businessDate));
        when(validation.validateCloseRequirements(date)).thenReturn(List.of());
        when(repository.save(any(BusinessDate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BusinessDate closed = service.closeBusinessDate(date);

        assertThat(closed.getStatus()).isEqualTo("CLOSED");
        assertThat(closed.getClosedAt()).isNotNull();
        verify(validation).validateCloseRequirements(date);
        verify(repository).save(businessDate);
    }

    private BusinessDateService service(BusinessDateRepository repository) {
        return new BusinessDateService(repository,
                mock(BusinessDateValidationService.class), mock(AuditLogService.class),
                new RolePermissionService(), mock(CurrentUserRoleService.class), fixedClock());
    }

    @Test
    void reportsHealthyOpenBusinessDate() {
        BusinessDateRepository repository = mock(BusinessDateRepository.class);
        when(repository.findByStatus("OPEN")).thenReturn(List.of(openDate(2026, 9, 7)));

        var health = service(repository).getHealth();

        assertThat(health.health()).isEqualTo(BusinessDateHealth.HEALTHY);
        assertThat(health.businessDate()).isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(health.expectedBusinessDate()).isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(health.stale()).isFalse();
        assertThat(health.staleByDays()).isZero();
    }

    @Test
    void reportsStaleOpenBusinessDateAndCalendarDateDifference() {
        BusinessDateRepository repository = mock(BusinessDateRepository.class);
        when(repository.findByStatus("OPEN")).thenReturn(List.of(openDate(2026, 9, 2)));

        var health = service(repository).getHealth();

        assertThat(health.health()).isEqualTo(BusinessDateHealth.STALE);
        assertThat(health.stale()).isTrue();
        assertThat(health.staleByDays()).isEqualTo(5);
        assertThat(service(repository).resolveBusinessDate(LocalDateTime.of(2026, 9, 8, 1, 0)))
                .isEqualTo(LocalDate.of(2026, 9, 2));
    }

    @Test
    void reportsMissingOpenBusinessDate() {
        BusinessDateRepository repository = mock(BusinessDateRepository.class);
        when(repository.findByStatus("OPEN")).thenReturn(List.of());

        var health = service(repository).getHealth();

        assertThat(health.health()).isEqualTo(BusinessDateHealth.MISSING);
        assertThat(health.businessDate()).isNull();
        assertThat(health.expectedBusinessDate()).isEqualTo(LocalDate.of(2026, 9, 7));
    }

    @Test
    void reportsMultipleOpenDatesAsInconsistentAndNeverSelectsOne() {
        BusinessDateRepository repository = mock(BusinessDateRepository.class);
        when(repository.findByStatus("OPEN")).thenReturn(List.of(
                openDate(2026, 9, 2), openDate(2026, 9, 7)));
        BusinessDateService service = service(repository);

        assertThat(service.getHealth().health()).isEqualTo(BusinessDateHealth.INCONSISTENT);
        assertThat(service.getHealth().businessDate()).isNull();
        assertThatThrownBy(service::getCurrentOpenBusinessDate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("multiple OPEN dates");
        assertThatThrownBy(service::getCurrentBusinessDate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("multiple OPEN dates");
    }

    @Test
    void reportsFutureOpenDateAsInconsistent() {
        BusinessDateRepository repository = mock(BusinessDateRepository.class);
        when(repository.findByStatus("OPEN")).thenReturn(List.of(openDate(2026, 9, 8)));

        var health = service(repository).getHealth();

        assertThat(health.health()).isEqualTo(BusinessDateHealth.INCONSISTENT);
        assertThat(health.businessDate()).isEqualTo(LocalDate.of(2026, 9, 8));
        assertThat(health.lifecycleWarning()).contains("later than");
    }

    @Test
    void expectedDateUsesKathmanduBoundaryIndependentlyOfJvmTimezone() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
            BusinessDateRepository repository = mock(BusinessDateRepository.class);
            when(repository.findByStatus("OPEN")).thenReturn(List.of());
            Clock beforeBoundary = Clock.fixed(
                    ZonedDateTime.of(2026, 9, 8, 8, 59, 59, 0, CASINO_ZONE).toInstant(),
                    ZoneId.of("UTC"));
            Clock atBoundary = Clock.fixed(
                    ZonedDateTime.of(2026, 9, 8, 9, 0, 0, 0, CASINO_ZONE).toInstant(),
                    ZoneId.of("UTC"));

            assertThat(service(repository, beforeBoundary).getExpectedBusinessDate())
                    .isEqualTo(LocalDate.of(2026, 9, 7));
            assertThat(service(repository, atBoundary).getExpectedBusinessDate())
                    .isEqualTo(LocalDate.of(2026, 9, 8));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    private BusinessDateService service(BusinessDateRepository repository, Clock clock) {
        return new BusinessDateService(repository,
                mock(BusinessDateValidationService.class), mock(AuditLogService.class),
                new RolePermissionService(), mock(CurrentUserRoleService.class), clock);
    }

    private Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-09-07T19:15:00Z"), ZoneId.of("UTC"));
    }

    private BusinessDate openDate(int year, int month, int day) {
        BusinessDate value = new BusinessDate();
        value.setBusinessDate(LocalDate.of(year, month, day));
        value.setStatus("OPEN");
        return value;
    }

    private java.time.Instant at(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, CASINO_ZONE).toInstant();
    }
}
