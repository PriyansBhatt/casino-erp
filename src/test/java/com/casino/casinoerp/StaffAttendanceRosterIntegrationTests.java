package com.casino.casinoerp;

import com.casino.casinoerp.dto.StaffAttendanceResponse;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.repository.*;
import com.casino.casinoerp.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StaffAttendanceRosterIntegrationTests {
    private static final ZoneId ZONE = ZoneId.of("Asia/Kathmandu");
    private final StaffAttendanceRepository attendance = mock(StaffAttendanceRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AuthenticatedUserService authenticatedUsers = mock(AuthenticatedUserService.class);
    private final BusinessDateService businessDates = mock(BusinessDateService.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final StaffProfileRepository staffProfiles = mock(StaffProfileRepository.class);
    private final StaffRosterAssignmentRepository rosters = mock(StaffRosterAssignmentRepository.class);
    private final ShiftDefinitionRepository shifts = mock(ShiftDefinitionRepository.class);
    private final User user = user();
    private final StaffProfile staff = staff();
    private MutableClock clock;
    private StaffAttendanceService service;

    @BeforeEach void setUp() {
        clock = new MutableClock(at(2026, 9, 10, 18, 0));
        service = new StaffAttendanceService(attendance, users, authenticatedUsers, businessDates,
                audit, staffProfiles, rosters, shifts, clock);
        when(authenticatedUsers.getRequiredUser()).thenReturn(user);
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(businessDates.resolveAttendanceBusinessDate(any())).thenReturn(LocalDate.of(2026, 9, 10));
        when(attendance.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(attendance.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(staffProfiles.findByUserId(user.getId())).thenReturn(Optional.of(staff));
        when(rosters.findByStaffProfileIdAndStatusAndRosterDateBetweenOrderByRosterDateAsc(
                eq(staff.getId()), eq(RosterStatus.SCHEDULED), any(), any())).thenReturn(List.of());
    }

    @Test void userWithoutStaffProfileChecksInUnscheduled() {
        when(staffProfiles.findByUserId(user.getId())).thenReturn(Optional.empty());
        StaffAttendanceResponse result = service.checkIn();
        assertThat(result.scheduled()).isFalse();
        assertThat(result.rosterAssignmentId()).isNull();
        assertThat(result.attendanceScheduleStatus()).isEqualTo(AttendanceScheduleStatus.UNSCHEDULED);
    }

    @Test void staffWithoutRosterChecksInUnscheduled() {
        StaffAttendanceResponse result = service.checkIn();
        assertThat(result.scheduled()).isFalse();
        assertThat(result.rosterAssignmentId()).isNull();
    }

    @Test void exactStartMatchesAndSnapshotsRoster() {
        StaffRosterAssignment roster = roster(LocalDate.of(2026, 9, 10), RosterStatus.SCHEDULED);
        ShiftDefinition shift = shift(LocalTime.of(18, 0), LocalTime.of(3, 30), true, 10, 30);
        candidates(roster, shift);
        StaffAttendanceResponse result = service.checkIn();
        assertThat(result.rosterAssignmentId()).isEqualTo(roster.getId());
        assertThat(result.shiftCode()).isEqualTo("NIGHT");
        assertThat(result.rosterDate()).isEqualTo(LocalDate.of(2026, 9, 10));
        assertThat(result.scheduledStartAt()).isEqualTo(at(2026, 9, 10, 18, 0));
        assertThat(result.scheduledEndAt()).isEqualTo(at(2026, 9, 11, 3, 30));
        assertThat(result.attendanceScheduleStatus()).isEqualTo(AttendanceScheduleStatus.ON_TIME);
    }

    @Test void allowedEarlyCheckInMatchesWithoutNegativeLateness() {
        clock.set(at(2026, 9, 10, 17, 30));
        candidates(roster(LocalDate.of(2026, 9, 10), RosterStatus.SCHEDULED),
                shift(LocalTime.of(18, 0), LocalTime.of(3, 30), true, 10, 30));
        StaffAttendanceResponse result = service.checkIn();
        assertThat(result.scheduled()).isTrue();
        assertThat(result.lateByMinutes()).isZero();
        assertThat(result.attendanceScheduleStatus()).isEqualTo(AttendanceScheduleStatus.ON_TIME);
    }

    @Test void ordinaryInProgressCheckInMatchesAndClassifiesLateBeyondGrace() {
        clock.set(at(2026, 9, 10, 18, 11));
        candidates(roster(LocalDate.of(2026, 9, 10), RosterStatus.SCHEDULED),
                shift(LocalTime.of(18, 0), LocalTime.of(23, 0), false, 10, 30));
        StaffAttendanceResponse result = service.checkIn();
        assertThat(result.lateByMinutes()).isEqualTo(11);
        assertThat(result.attendanceScheduleStatus()).isEqualTo(AttendanceScheduleStatus.LATE);
    }

    @Test void checkInWithinGraceIsOnTimeButReportsActualMinutesAfterStart() {
        clock.set(at(2026, 9, 10, 18, 9));
        candidates(roster(LocalDate.of(2026, 9, 10), RosterStatus.SCHEDULED),
                shift(LocalTime.of(18, 0), LocalTime.of(23, 0), false, 10, 30));
        StaffAttendanceResponse result = service.checkIn();
        assertThat(result.lateByMinutes()).isEqualTo(9);
        assertThat(result.attendanceScheduleStatus()).isEqualTo(AttendanceScheduleStatus.ON_TIME);
    }

    @Test void checkInExactlyAtGraceBoundaryIsOnTime() {
        clock.set(at(2026, 9, 10, 18, 10));
        candidates(roster(LocalDate.of(2026, 9, 10), RosterStatus.SCHEDULED),
                shift(LocalTime.of(18, 0), LocalTime.of(23, 0), false, 10, 30));
        StaffAttendanceResponse result = service.checkIn();
        assertThat(result.lateByMinutes()).isEqualTo(10);
        assertThat(result.attendanceScheduleStatus()).isEqualTo(AttendanceScheduleStatus.ON_TIME);
    }

    @Test void overnightRosterMatchesAfterMidnight() {
        clock.set(at(2026, 9, 11, 0, 30));
        StaffRosterAssignment roster = roster(LocalDate.of(2026, 9, 10), RosterStatus.SCHEDULED);
        candidates(roster, shift(LocalTime.of(18, 0), LocalTime.of(3, 30), true, 10, 30));
        StaffAttendanceResponse result = service.checkIn();
        assertThat(result.rosterAssignmentId()).isEqualTo(roster.getId());
        assertThat(result.rosterDate()).isEqualTo(LocalDate.of(2026, 9, 10));
    }

    @Test void cancelledRosterIsIgnoredByScheduledCandidateQuery() {
        StaffRosterAssignment cancelled = roster(LocalDate.of(2026, 9, 10), RosterStatus.CANCELLED);
        ShiftDefinition shift = shift(LocalTime.of(18, 0), LocalTime.of(23, 0), false, 10, 30);
        when(rosters.findByStaffProfileIdAndStatusAndRosterDateBetweenOrderByRosterDateAsc(any(), any(), any(), any()))
                .thenReturn(List.of(cancelled));
        when(shifts.findById(cancelled.getShiftDefinitionId())).thenReturn(Optional.of(shift));
        StaffAttendanceResponse result = service.checkIn();
        assertThat(result.scheduled()).isFalse();
        verify(rosters).findByStaffProfileIdAndStatusAndRosterDateBetweenOrderByRosterDateAsc(
                staff.getId(), RosterStatus.SCHEDULED, LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 11));
    }

    @Test void exactSecondMatchBoundariesAreEnforced() {
        StaffRosterAssignment roster = roster(LocalDate.of(2026, 9, 10), RosterStatus.SCHEDULED);
        ShiftDefinition shift = shift(LocalTime.of(18, 0), LocalTime.of(3, 30), true, 10, 30);
        candidates(roster, shift);
        clock.set(at(2026, 9, 10, 17, 29, 59));
        assertThat(service.checkIn().scheduled()).isFalse();
        reset(attendance);
        when(attendance.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        clock.set(at(2026, 9, 10, 17, 30, 0));
        assertThat(service.checkIn().scheduled()).isTrue();
        reset(attendance);
        when(attendance.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        clock.set(at(2026, 9, 11, 3, 29, 59));
        assertThat(service.checkIn().scheduled()).isTrue();
        reset(attendance);
        when(attendance.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        clock.set(at(2026, 9, 11, 3, 30, 0));
        assertThat(service.checkIn().scheduled()).isFalse();
    }

    @Test void ambiguousMatchesRemainUnscheduled() {
        StaffRosterAssignment first = roster(LocalDate.of(2026, 9, 10), RosterStatus.SCHEDULED);
        StaffRosterAssignment second = roster(LocalDate.of(2026, 9, 10), RosterStatus.SCHEDULED);
        ShiftDefinition shift = shift(LocalTime.of(17, 0), LocalTime.of(23, 0), false, 10, 30);
        when(rosters.findByStaffProfileIdAndStatusAndRosterDateBetweenOrderByRosterDateAsc(any(), any(), any(), any()))
                .thenReturn(List.of(first, second));
        when(shifts.findById(first.getShiftDefinitionId())).thenReturn(Optional.of(shift));
        when(shifts.findById(second.getShiftDefinitionId())).thenReturn(Optional.of(shift));
        StaffAttendanceResponse result = service.checkIn();
        assertThat(result.scheduled()).isFalse();
        assertThat(result.rosterAssignmentId()).isNull();
    }

    @Test void snapshotSurvivesLaterShiftChangeAndRosterCancellation() {
        StaffRosterAssignment roster = roster(LocalDate.of(2026, 9, 10), RosterStatus.SCHEDULED);
        ShiftDefinition shift = shift(LocalTime.of(18, 0), LocalTime.of(3, 30), true, 10, 30);
        candidates(roster, shift);
        StaffAttendanceResponse opened = service.checkIn();
        StaffAttendance stored = capturedAttendance();
        roster.setStatus(RosterStatus.CANCELLED);
        shift.setStartTime(LocalTime.of(20, 0));
        shift.setEndTime(LocalTime.of(4, 0));
        when(attendance.findOpenByUserIdForUpdate(user.getId(), StaffAttendanceStatus.OPEN))
                .thenReturn(Optional.of(stored));
        clock.set(at(2026, 9, 11, 3, 0));
        StaffAttendanceResponse closed = service.checkOut();
        assertThat(closed.rosterAssignmentId()).isEqualTo(opened.rosterAssignmentId());
        assertThat(closed.scheduledStartAt()).isEqualTo(at(2026, 9, 10, 18, 0));
        assertThat(closed.scheduledEndAt()).isEqualTo(at(2026, 9, 11, 3, 30));
        assertThat(closed.earlyDepartureMinutes()).isEqualTo(30);
        assertThat(closed.attendanceScheduleStatus()).isEqualTo(AttendanceScheduleStatus.EARLY_DEPARTURE);

        user.setRole("DIRECTOR");
        when(attendance.findByBusinessDateOrderByCheckInAtAsc(stored.getBusinessDate()))
                .thenReturn(List.of(stored));
        when(attendance.findByUserIdOrderByCheckInAtDesc(user.getId())).thenReturn(List.of(stored));
        when(attendance.findByUserIdAndBusinessDateOrderByCheckInAtDesc(user.getId(), stored.getBusinessDate()))
                .thenReturn(List.of(stored));
        clearInvocations(users, staffProfiles, rosters, shifts);
        assertThat(service.report(stored.getBusinessDate())).containsExactly(closed);
        assertThat(service.myHistory(null)).containsExactly(closed);
        assertThat(service.myHistory(stored.getBusinessDate())).containsExactly(closed);
        verifyNoInteractions(users, staffProfiles, rosters, shifts);
    }

    @Test void openAttendanceNeverReportsEarlyDeparture() {
        candidates(roster(LocalDate.of(2026, 9, 10), RosterStatus.SCHEDULED),
                shift(LocalTime.of(18, 0), LocalTime.of(23, 0), false, 10, 30));
        StaffAttendanceResponse result = service.checkIn();
        assertThat(result.earlyDepartureMinutes()).isNull();
        assertThat(result.attendanceScheduleStatus()).isEqualTo(AttendanceScheduleStatus.ON_TIME);
    }

    @Test void lateAndEarlyDepartureClassificationIsCombined() {
        clock.set(at(2026, 9, 10, 18, 20));
        candidates(roster(LocalDate.of(2026, 9, 10), RosterStatus.SCHEDULED),
                shift(LocalTime.of(18, 0), LocalTime.of(23, 0), false, 10, 30));
        service.checkIn();
        StaffAttendance stored = capturedAttendance();
        when(attendance.findOpenByUserIdForUpdate(user.getId(), StaffAttendanceStatus.OPEN))
                .thenReturn(Optional.of(stored));
        clock.set(at(2026, 9, 10, 22, 0));
        StaffAttendanceResponse result = service.checkOut();
        assertThat(result.attendanceScheduleStatus()).isEqualTo(AttendanceScheduleStatus.LATE_AND_EARLY_DEPARTURE);
        assertThat(result.lateByMinutes()).isEqualTo(20);
        assertThat(result.earlyDepartureMinutes()).isEqualTo(60);
    }

    private void candidates(StaffRosterAssignment roster, ShiftDefinition shift) {
        when(rosters.findByStaffProfileIdAndStatusAndRosterDateBetweenOrderByRosterDateAsc(any(), any(), any(), any()))
                .thenReturn(List.of(roster));
        when(shifts.findById(roster.getShiftDefinitionId())).thenReturn(Optional.of(shift));
    }

    private StaffAttendance capturedAttendance() {
        var captor = org.mockito.ArgumentCaptor.forClass(StaffAttendance.class);
        verify(attendance).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private User user() { User value = new User(); value.setId(UUID.randomUUID()); value.setUsername("employee"); value.setFullName("Employee"); value.setStatus("ACTIVE"); value.setRole("CASHIER"); return value; }
    private StaffProfile staff() { StaffProfile value = new StaffProfile(); value.setId(UUID.randomUUID()); value.setUserId(user.getId()); value.setEmploymentStatus(EmploymentStatus.ACTIVE); return value; }
    private StaffRosterAssignment roster(LocalDate date, RosterStatus status) { StaffRosterAssignment value = new StaffRosterAssignment(); value.setId(UUID.randomUUID()); value.setStaffProfileId(staff.getId()); value.setShiftDefinitionId(UUID.randomUUID()); value.setRosterDate(date); value.setStatus(status); return value; }
    private ShiftDefinition shift(LocalTime start, LocalTime end, boolean crosses, int grace, int early) { ShiftDefinition value = new ShiftDefinition(); value.setId(UUID.randomUUID()); value.setCode("NIGHT"); value.setName("Night Shift"); value.setStartTime(start); value.setEndTime(end); value.setCrossesMidnight(crosses); value.setLateGraceMinutes(grace); value.setEarlyCheckInMinutes(early); return value; }
    private Instant at(int year, int month, int day, int hour, int minute) { return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZONE).toInstant(); }
    private Instant at(int year, int month, int day, int hour, int minute, int second) { return ZonedDateTime.of(year, month, day, hour, minute, second, 0, ZONE).toInstant(); }
    private static final class MutableClock extends Clock { private Instant instant; private MutableClock(Instant instant) { this.instant = instant; } private void set(Instant value) { instant = value; } @Override public ZoneId getZone() { return ZONE; } @Override public Clock withZone(ZoneId zone) { return this; } @Override public Instant instant() { return instant; } }
}
