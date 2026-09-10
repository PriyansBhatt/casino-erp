package com.casino.casinoerp;

import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.exception.*;
import com.casino.casinoerp.repository.*;
import com.casino.casinoerp.security.Role;
import com.casino.casinoerp.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StaffAttendanceCorrectionServiceTests {
    private final StaffAttendanceRepository attendance = mock(StaffAttendanceRepository.class);
    private final StaffAttendanceCorrectionRepository corrections = mock(StaffAttendanceCorrectionRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AuthenticatedUserService authenticatedUsers = mock(AuthenticatedUserService.class);
    private final CurrentUserRoleService currentRoles = mock(CurrentUserRoleService.class);
    private final RolePermissionService permissions = new RolePermissionService();
    private final AuditLogService audit = mock(AuditLogService.class);
    private final User actor = user("director", "DIRECTOR");
    private final User employee = user("employee", "CASHIER");
    private final Instant now = Instant.parse("2026-09-12T12:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final UUID attendanceId = UUID.randomUUID();
    private StaffAttendanceCorrectionService service;

    @BeforeEach void setUp() {
        service = new StaffAttendanceCorrectionService(attendance, corrections, users,
                authenticatedUsers, currentRoles, permissions, audit, clock);
        when(currentRoles.getCurrentRole()).thenReturn(Optional.of(Role.DIRECTOR));
        when(authenticatedUsers.getRequiredUser()).thenReturn(actor);
        when(users.findById(actor.getId())).thenReturn(Optional.of(actor));
        when(attendance.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(corrections.saveAndFlush(any())).thenAnswer(invocation -> {
            StaffAttendanceCorrection value = invocation.getArgument(0);
            if (value.getId() == null) value.setId(UUID.randomUUID());
            return value;
        });
    }

    @Test void missedCheckoutClosesOpenAttendanceAndPreservesAuthority() {
        StaffAttendance value = open(at("2026-09-10T12:15:00Z"));
        UUID rosterId = value.getRosterAssignmentId();
        LocalDate businessDate = value.getBusinessDate();
        Instant scheduledStart = value.getScheduledStartAt();
        when(attendance.findByIdForUpdate(attendanceId)).thenReturn(Optional.of(value));

        StaffAttendanceCorrectionResponse result = service.correct(attendanceId,
                request(StaffAttendanceCorrectionType.MISSED_CHECKOUT, null,
                        at("2026-09-10T21:45:00Z"), " Employee forgot to check out "));

        assertThat(value.getStatus()).isEqualTo(StaffAttendanceStatus.CLOSED);
        assertThat(value.getCheckOutAt()).isEqualTo(at("2026-09-10T21:45:00Z"));
        assertThat(result.newWorkedMinutes()).isEqualTo(570);
        assertThat(value.getBusinessDate()).isEqualTo(businessDate);
        assertThat(value.getRosterAssignmentId()).isEqualTo(rosterId);
        assertThat(value.getScheduledStartAt()).isEqualTo(scheduledStart);
        assertThat(result.reason()).isEqualTo("Employee forgot to check out");
        verify(attendance).findByIdForUpdate(attendanceId);
        verify(corrections).saveAndFlush(any());
        verify(audit).log(eq("CORRECT_STAFF_ATTENDANCE"), eq("STAFF_ATTENDANCE"),
                eq(attendanceId), eq(actor.getId()), contains("MISSED_CHECKOUT"));
    }

    @Test void closedAttendanceSupportsEachBoundedCorrectionType() {
        StaffAttendance checkIn = closed(at("2026-09-10T12:15:00Z"), at("2026-09-10T21:45:00Z"));
        when(attendance.findByIdForUpdate(attendanceId)).thenReturn(Optional.of(checkIn));
        service.correct(attendanceId, request(StaffAttendanceCorrectionType.CHECK_IN_TIME,
                at("2026-09-10T12:05:00Z"), null, "Correct check-in"));
        assertThat(checkIn.getCheckInAt()).isEqualTo(at("2026-09-10T12:05:00Z"));

        StaffAttendance checkOut = closed(at("2026-09-10T12:15:00Z"), at("2026-09-10T21:45:00Z"));
        when(attendance.findByIdForUpdate(attendanceId)).thenReturn(Optional.of(checkOut));
        service.correct(attendanceId, request(StaffAttendanceCorrectionType.CHECK_OUT_TIME,
                null, at("2026-09-10T21:35:00Z"), "Correct checkout"));
        assertThat(checkOut.getCheckOutAt()).isEqualTo(at("2026-09-10T21:35:00Z"));

        StaffAttendance both = closed(at("2026-09-10T12:15:00Z"), at("2026-09-10T21:45:00Z"));
        when(attendance.findByIdForUpdate(attendanceId)).thenReturn(Optional.of(both));
        service.correct(attendanceId, request(StaffAttendanceCorrectionType.CHECK_IN_AND_OUT,
                at("2026-09-10T12:00:00Z"), at("2026-09-10T21:30:00Z"), "Correct both"));
        assertThat(both.getCheckInAt()).isEqualTo(at("2026-09-10T12:00:00Z"));
        assertThat(both.getCheckOutAt()).isEqualTo(at("2026-09-10T21:30:00Z"));
        verify(corrections, times(3)).saveAndFlush(any());
    }

    @Test void openCheckInCorrectionLeavesAttendanceOpenAndUnworked() {
        StaffAttendance value = open(at("2026-09-10T12:15:00Z"));
        when(attendance.findByIdForUpdate(attendanceId)).thenReturn(Optional.of(value));
        StaffAttendanceCorrectionResponse result = service.correct(attendanceId,
                request(StaffAttendanceCorrectionType.CHECK_IN_TIME,
                        at("2026-09-10T12:05:00Z"), null, "Correct check-in"));
        assertThat(value.getStatus()).isEqualTo(StaffAttendanceStatus.OPEN);
        assertThat(value.getCheckOutAt()).isNull();
        assertThat(result.newWorkedMinutes()).isNull();
    }

    @Test void invalidIntervalFutureAndUnchangedRequestsAreRejectedWithoutHistory() {
        StaffAttendance value = closed(at("2026-09-10T12:15:00Z"), at("2026-09-10T21:45:00Z"));
        when(attendance.findByIdForUpdate(attendanceId)).thenReturn(Optional.of(value));
        assertThatThrownBy(() -> service.correct(attendanceId,
                request(StaffAttendanceCorrectionType.CHECK_OUT_TIME, null,
                        at("2026-09-10T12:00:00Z"), "Invalid interval")))
                .hasMessageContaining("later than check-in");
        assertThatThrownBy(() -> service.correct(attendanceId,
                request(StaffAttendanceCorrectionType.CHECK_IN_TIME,
                        now.plusSeconds(1), null, "Future")))
                .hasMessageContaining("future");
        assertThatThrownBy(() -> service.correct(attendanceId,
                request(StaffAttendanceCorrectionType.CHECK_OUT_TIME, null,
                        value.getCheckOutAt(), "Unchanged")))
                .isInstanceOf(ResourceConflictException.class).hasMessageContaining("does not change");
        verify(corrections, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    @Test void reasonAndTypeSpecificLifecycleAreValidated() {
        StaffAttendance open = open(at("2026-09-10T12:15:00Z"));
        when(attendance.findByIdForUpdate(attendanceId)).thenReturn(Optional.of(open));
        assertThatThrownBy(() -> service.correct(attendanceId,
                request(StaffAttendanceCorrectionType.CHECK_IN_TIME,
                        at("2026-09-10T12:05:00Z"), null, "   "))).hasMessageContaining("reason");
        assertThatThrownBy(() -> service.correct(attendanceId,
                request(StaffAttendanceCorrectionType.CHECK_OUT_TIME,
                        null, at("2026-09-10T21:45:00Z"), "Wrong type")))
                .isInstanceOf(ResourceConflictException.class);
        assertThatThrownBy(() -> service.correct(attendanceId,
                request(StaffAttendanceCorrectionType.CHECK_IN_AND_OUT,
                        at("2026-09-10T12:05:00Z"), at("2026-09-10T21:45:00Z"), "Wrong type")))
                .isInstanceOf(ResourceConflictException.class);
    }

    @Test void sequentialCorrectionsAppendHistoryAndUseLatestPersistedValue() {
        StaffAttendance value = open(at("2026-09-10T12:15:00Z"));
        when(attendance.findByIdForUpdate(attendanceId)).thenReturn(Optional.of(value));
        service.correct(attendanceId, request(StaffAttendanceCorrectionType.MISSED_CHECKOUT,
                null, at("2026-09-10T21:45:00Z"), "Forgot checkout"));
        StaffAttendanceCorrectionResponse second = service.correct(attendanceId,
                request(StaffAttendanceCorrectionType.CHECK_OUT_TIME,
                        null, at("2026-09-10T21:35:00Z"), "Refined checkout"));
        assertThat(second.previousCheckOutAt()).isEqualTo(at("2026-09-10T21:45:00Z"));
        assertThat(second.newCheckOutAt()).isEqualTo(at("2026-09-10T21:35:00Z"));
        verify(corrections, times(2)).saveAndFlush(any());
    }

    @Test void correctedActualTimesDriveClassificationWithoutChangingScheduleSnapshot() {
        StaffAttendance value = closed(at("2026-09-10T12:30:00Z"), at("2026-09-10T21:45:00Z"));
        UUID rosterId = value.getRosterAssignmentId();
        Instant scheduledStart = value.getScheduledStartAt();
        Instant scheduledEnd = value.getScheduledEndAt();
        when(attendance.findByIdForUpdate(attendanceId)).thenReturn(Optional.of(value));

        service.correct(attendanceId, request(StaffAttendanceCorrectionType.CHECK_IN_AND_OUT,
                at("2026-09-10T12:20:00Z"), at("2026-09-10T21:15:00Z"), "Correct actual times"));

        when(authenticatedUsers.getRequiredUser()).thenReturn(employee);
        when(attendance.findByUserIdAndBusinessDateOrderByCheckInAtDesc(
                employee.getId(), value.getBusinessDate())).thenReturn(List.of(value));
        StaffAttendanceService attendanceService = new StaffAttendanceService(attendance, users,
                authenticatedUsers, mock(BusinessDateService.class), audit,
                mock(StaffProfileRepository.class), mock(StaffRosterAssignmentRepository.class),
                mock(ShiftDefinitionRepository.class), clock);
        StaffAttendanceResponse response = attendanceService.myHistory(value.getBusinessDate()).getFirst();

        assertThat(response.attendanceScheduleStatus()).isEqualTo(AttendanceScheduleStatus.EARLY_DEPARTURE);
        assertThat(response.lateByMinutes()).isEqualTo(5);
        assertThat(response.earlyDepartureMinutes()).isEqualTo(30);
        assertThat(value.getRosterAssignmentId()).isEqualTo(rosterId);
        assertThat(value.getScheduledStartAt()).isEqualTo(scheduledStart);
        assertThat(value.getScheduledEndAt()).isEqualTo(scheduledEnd);
    }

    @Test void correctedTimestampDoesNotMoveAttendanceBetweenBusinessDateReports() {
        StaffAttendance value = closed(at("2026-09-10T12:15:00Z"), at("2026-09-10T21:45:00Z"));
        LocalDate originalBusinessDate = value.getBusinessDate();
        when(attendance.findByIdForUpdate(attendanceId)).thenReturn(Optional.of(value));

        service.correct(attendanceId, request(StaffAttendanceCorrectionType.CHECK_IN_TIME,
                at("2026-09-09T23:45:00Z"), null, "Correct cross-calendar check-in"));

        when(attendance.findByBusinessDateOrderByCheckInAtAsc(originalBusinessDate)).thenReturn(List.of(value));
        StaffAttendanceService attendanceService = new StaffAttendanceService(attendance, users,
                authenticatedUsers, mock(BusinessDateService.class), audit,
                mock(StaffProfileRepository.class), mock(StaffRosterAssignmentRepository.class),
                mock(ShiftDefinitionRepository.class), clock);
        StaffAttendanceResponse response = attendanceService.report(originalBusinessDate).getFirst();

        assertThat(response.businessDate()).isEqualTo(originalBusinessDate);
        assertThat(response.checkInAt()).isEqualTo(at("2026-09-09T23:45:00Z"));
        verify(attendance).findByBusinessDateOrderByCheckInAtAsc(originalBusinessDate);
    }

    @Test void correctionRequiresManagementRoleAndDoesNotRequireStaffProfile() {
        when(currentRoles.getCurrentRole()).thenReturn(Optional.of(Role.CASHIER));
        assertThatThrownBy(() -> service.correct(attendanceId,
                request(StaffAttendanceCorrectionType.CHECK_IN_TIME,
                        at("2026-09-10T12:05:00Z"), null, "Correction")))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verify(attendance, never()).findByIdForUpdate(any());
    }

    @Test void historyIsImmutableOrderedAndManagementOnly() {
        StaffAttendanceCorrection value = new StaffAttendanceCorrection();
        value.setId(UUID.randomUUID()); value.setAttendanceId(attendanceId);
        value.setCorrectionType(StaffAttendanceCorrectionType.CHECK_IN_TIME);
        value.setPreviousCheckInAt(at("2026-09-10T12:15:00Z"));
        value.setNewCheckInAt(at("2026-09-10T12:05:00Z")); value.setReason("Correction");
        value.setCorrectedByUserId(actor.getId()); value.setCorrectedAt(now);
        when(attendance.existsById(attendanceId)).thenReturn(true);
        when(corrections.findByAttendanceIdOrderByCorrectedAtAsc(attendanceId)).thenReturn(List.of(value));
        assertThat(service.history(attendanceId)).singleElement()
                .extracting(StaffAttendanceCorrectionResponse::correctionId).isEqualTo(value.getId());
    }

    private StaffAttendance open(Instant checkIn) {
        StaffAttendance value = base(checkIn); value.setStatus(StaffAttendanceStatus.OPEN); return value;
    }
    private StaffAttendance closed(Instant checkIn, Instant checkOut) {
        StaffAttendance value = base(checkIn); value.setStatus(StaffAttendanceStatus.CLOSED); value.setCheckOutAt(checkOut); return value;
    }
    private StaffAttendance base(Instant checkIn) {
        StaffAttendance value = new StaffAttendance(); value.setId(attendanceId); value.setUser(employee);
        value.setBusinessDate(LocalDate.of(2026, 9, 10)); value.setCheckInAt(checkIn);
        value.setCreatedAt(checkIn); value.setUpdatedAt(checkIn); value.setRosterAssignmentId(UUID.randomUUID());
        value.setRosterDate(LocalDate.of(2026, 9, 10)); value.setShiftCode("NIGHT"); value.setShiftName("Night Shift");
        value.setScheduledStartAt(at("2026-09-10T12:15:00Z")); value.setScheduledEndAt(at("2026-09-10T21:45:00Z"));
        value.setRosterLateGraceMinutes(10); value.setRosterEarlyCheckInMinutes(30); return value;
    }
    private CreateStaffAttendanceCorrectionRequest request(StaffAttendanceCorrectionType type,
            Instant checkIn, Instant checkOut, String reason) {
        return new CreateStaffAttendanceCorrectionRequest(type, checkIn, checkOut, reason);
    }
    private Instant at(String value) { return Instant.parse(value); }
    private User user(String username, String role) { User value = new User(); value.setId(UUID.randomUUID()); value.setUsername(username); value.setFullName(username); value.setRole(role); value.setStatus("ACTIVE"); return value; }
}
