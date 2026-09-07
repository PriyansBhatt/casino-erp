package com.casino.casinoerp;

import com.casino.casinoerp.dto.StaffAttendanceResponse;
import com.casino.casinoerp.entity.StaffAttendance;
import com.casino.casinoerp.entity.StaffAttendanceStatus;
import com.casino.casinoerp.entity.User;
import com.casino.casinoerp.exception.ResourceConflictException;
import com.casino.casinoerp.repository.StaffAttendanceRepository;
import com.casino.casinoerp.repository.UserRepository;
import com.casino.casinoerp.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StaffAttendanceServiceTests {
    private static final ZoneId ZONE = ZoneId.of("Asia/Kathmandu");
    private final StaffAttendanceRepository attendance = mock(StaffAttendanceRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AuthenticatedUserService authenticatedUsers = mock(AuthenticatedUserService.class);
    private final BusinessDateService businessDates = mock(BusinessDateService.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final User user = user("ACTIVE", "CASHIER");
    private MutableClock clock;
    private StaffAttendanceService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(at(2026, 9, 7, 12, 30));
        service = new StaffAttendanceService(attendance, users, authenticatedUsers,
                businessDates, audit, clock);
        when(authenticatedUsers.getRequiredUser()).thenReturn(user);
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(businessDates.resolveAttendanceBusinessDate(any())).thenReturn(LocalDate.of(2026, 9, 7));
        when(attendance.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(attendance.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void sameCalendarDayShiftClosesAsOneRecord() {
        StaffAttendanceResponse opened = service.checkIn();
        when(attendance.findOpenByUserIdForUpdate(user.getId(), StaffAttendanceStatus.OPEN))
                .thenReturn(Optional.of(entity(opened)));
        clock.set(at(2026, 9, 7, 20, 0));

        StaffAttendanceResponse closed = service.checkOut();

        assertThat(closed.status()).isEqualTo(StaffAttendanceStatus.CLOSED);
        assertThat(closed.businessDate()).isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(closed.workedMinutes()).isEqualTo(450);
        verify(attendance, times(1)).saveAndFlush(any());
        verify(attendance, times(1)).save(any());
    }

    @Test
    void crossMidnightShiftKeepsCheckInBusinessDateAndLasts570Minutes() {
        clock.set(at(2026, 9, 7, 18, 0));
        StaffAttendanceResponse opened = service.checkIn();
        StaffAttendance stored = entity(opened);
        when(attendance.findOpenByUserIdForUpdate(user.getId(), StaffAttendanceStatus.OPEN))
                .thenReturn(Optional.of(stored));
        clock.set(at(2026, 9, 8, 3, 30));

        StaffAttendanceResponse closed = service.checkOut();

        assertThat(closed.attendanceId()).isEqualTo(opened.attendanceId());
        assertThat(closed.businessDate()).isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(closed.status()).isEqualTo(StaffAttendanceStatus.CLOSED);
        assertThat(closed.workedMinutes()).isEqualTo(570);
        verify(businessDates, times(1)).resolveAttendanceBusinessDate(any());
    }

    @Test
    void duplicateOpenShiftIsRejectedBeforeInsert() {
        when(attendance.findFirstByUserIdAndStatusOrderByCheckInAtDesc(
                user.getId(), StaffAttendanceStatus.OPEN)).thenReturn(Optional.of(new StaffAttendance()));

        assertThatThrownBy(service::checkIn).isInstanceOf(ResourceConflictException.class)
                .hasMessage("Employee already has an OPEN attendance shift.");
        verify(attendance, never()).saveAndFlush(any());
    }

    @Test
    void databaseDuplicateProtectionBecomesDeterministicConflict() {
        when(attendance.saveAndFlush(any())).thenThrow(
                new org.springframework.dao.DataIntegrityViolationException("unique index"));

        assertThatThrownBy(service::checkIn).isInstanceOf(ResourceConflictException.class)
                .hasMessage("Employee already has an OPEN attendance shift.");
    }

    @Test
    void checkoutWithoutOpenShiftAndDoubleCheckoutAreRejected() {
        when(attendance.findOpenByUserIdForUpdate(user.getId(), StaffAttendanceStatus.OPEN))
                .thenReturn(Optional.empty());

        assertThatThrownBy(service::checkOut).isInstanceOf(ResourceConflictException.class)
                .hasMessage("Employee has no OPEN attendance shift.");
        assertThatThrownBy(service::checkOut).isInstanceOf(ResourceConflictException.class)
                .hasMessage("Employee has no OPEN attendance shift.");
    }

    @Test
    void checkoutMustBeLaterThanCheckin() {
        StaffAttendance value = new StaffAttendance();
        value.setUser(user); value.setCheckInAt(clock.instant());
        when(attendance.findOpenByUserIdForUpdate(user.getId(), StaffAttendanceStatus.OPEN))
                .thenReturn(Optional.of(value));

        assertThatThrownBy(service::checkOut).isInstanceOf(ResourceConflictException.class)
                .hasMessage("Check-out time must be later than check-in time.");
    }

    @Test
    void inactiveUserCannotCheckIn() {
        User inactive = user("INACTIVE", "CASHIER");
        when(authenticatedUsers.getRequiredUser()).thenReturn(inactive);
        when(users.findByIdForUpdate(inactive.getId())).thenReturn(Optional.of(inactive));

        assertThatThrownBy(service::checkIn)
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessage("Only ACTIVE users may start an attendance shift.");
    }

    @Test
    void reportUsesPersistedBusinessDateAndIsRestrictedInService() {
        LocalDate date = LocalDate.of(2026, 9, 7);
        StaffAttendance value = new StaffAttendance();
        value.setId(UUID.randomUUID()); value.setUser(user); value.setBusinessDate(date);
        value.setStatus(StaffAttendanceStatus.OPEN); value.setCheckInAt(clock.instant());
        value.setCreatedAt(clock.instant()); value.setUpdatedAt(clock.instant());
        when(attendance.findByBusinessDateOrderByCheckInAtAsc(date)).thenReturn(List.of(value));

        assertThatThrownBy(() -> service.report(date))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        user.setRole("DIRECTOR");
        assertThat(service.report(date)).hasSize(1);
        verify(attendance).findByBusinessDateOrderByCheckInAtAsc(date);
    }

    @Test
    void selfHistoryCanOnlyQueryAuthenticatedUser() {
        LocalDate date = LocalDate.of(2026, 9, 7);
        when(attendance.findByUserIdAndBusinessDateOrderByCheckInAtDesc(user.getId(), date))
                .thenReturn(List.of());

        service.myHistory(date);

        verify(attendance).findByUserIdAndBusinessDateOrderByCheckInAtDesc(user.getId(), date);
    }

    private StaffAttendance entity(StaffAttendanceResponse response) {
        StaffAttendance value = new StaffAttendance();
        value.setId(response.attendanceId()); value.setUser(user);
        value.setBusinessDate(response.businessDate()); value.setStatus(response.status());
        value.setCheckInAt(response.checkInAt()); value.setCheckOutAt(response.checkOutAt());
        value.setCreatedAt(response.createdAt()); value.setUpdatedAt(response.updatedAt());
        return value;
    }

    private User user(String status, String role) {
        User value = new User(); value.setId(UUID.randomUUID()); value.setUsername("employee");
        value.setFullName("Employee User"); value.setStatus(status); value.setRole(role);
        return value;
    }

    private Instant at(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZONE).toInstant();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private MutableClock(Instant instant) { this.instant = instant; }
        private void set(Instant value) { instant = value; }
        @Override public ZoneId getZone() { return ZONE; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
