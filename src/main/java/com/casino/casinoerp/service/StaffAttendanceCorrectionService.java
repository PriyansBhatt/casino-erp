package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.exception.*;
import com.casino.casinoerp.repository.*;
import com.casino.casinoerp.security.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
public class StaffAttendanceCorrectionService {
    private final StaffAttendanceRepository attendanceRepository;
    private final StaffAttendanceCorrectionRepository corrections;
    private final UserRepository users;
    private final AuthenticatedUserService authenticatedUsers;
    private final CurrentUserRoleService currentRoles;
    private final RolePermissionService permissions;
    private final AuditLogService audit;
    private final Clock clock;

    @Autowired
    public StaffAttendanceCorrectionService(StaffAttendanceRepository attendanceRepository,
            StaffAttendanceCorrectionRepository corrections, UserRepository users,
            AuthenticatedUserService authenticatedUsers, CurrentUserRoleService currentRoles,
            RolePermissionService permissions, AuditLogService audit) {
        this(attendanceRepository, corrections, users, authenticatedUsers, currentRoles,
                permissions, audit, Clock.systemUTC());
    }

    public StaffAttendanceCorrectionService(StaffAttendanceRepository attendanceRepository,
            StaffAttendanceCorrectionRepository corrections, UserRepository users,
            AuthenticatedUserService authenticatedUsers, CurrentUserRoleService currentRoles,
            RolePermissionService permissions, AuditLogService audit, Clock clock) {
        this.attendanceRepository = attendanceRepository;
        this.corrections = corrections;
        this.users = users;
        this.authenticatedUsers = authenticatedUsers;
        this.currentRoles = currentRoles;
        this.permissions = permissions;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public StaffAttendanceCorrectionResponse correct(UUID attendanceId,
            CreateStaffAttendanceCorrectionRequest request) {
        requireManager();
        StaffAttendance attendance = attendanceRepository.findByIdForUpdate(attendanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff Attendance not found."));
        validateRequestShape(attendance, request);
        validateNotFuture(request.checkInAt());
        validateNotFuture(request.checkOutAt());

        Instant previousCheckIn = attendance.getCheckInAt();
        Instant previousCheckOut = attendance.getCheckOutAt();
        Long previousWorked = workedMinutes(previousCheckIn, previousCheckOut);
        Instant nextCheckIn = request.checkInAt() == null ? previousCheckIn : request.checkInAt();
        Instant nextCheckOut = request.checkOutAt() == null ? previousCheckOut : request.checkOutAt();
        validateChanged(request.type(), previousCheckIn, previousCheckOut, nextCheckIn, nextCheckOut);
        validateInterval(nextCheckIn, nextCheckOut);

        attendance.setCheckInAt(nextCheckIn);
        if (request.type() == StaffAttendanceCorrectionType.MISSED_CHECKOUT) {
            attendance.setStatus(StaffAttendanceStatus.CLOSED);
        }
        attendance.setCheckOutAt(nextCheckOut);
        attendance.setUpdatedAt(clock.instant());
        attendanceRepository.save(attendance);

        User actor = authenticatedUsers.getRequiredUser();
        StaffAttendanceCorrection correction = new StaffAttendanceCorrection();
        correction.setAttendanceId(attendance.getId());
        correction.setCorrectionType(request.type());
        populateChangedValues(correction, request.type(), previousCheckIn, previousCheckOut,
                nextCheckIn, nextCheckOut);
        correction.setPreviousWorkedMinutes(previousWorked);
        correction.setNewWorkedMinutes(workedMinutes(nextCheckIn, nextCheckOut));
        correction.setReason(request.reason().trim());
        correction.setCorrectedByUserId(actor.getId());
        correction.setCorrectedAt(clock.instant());
        correction = corrections.saveAndFlush(correction);
        audit.log("CORRECT_STAFF_ATTENDANCE", "STAFF_ATTENDANCE", attendance.getId(), actor.getId(),
                "CorrectionId=" + correction.getId() + ", type=" + correction.getCorrectionType()
                        + ", previousCheckInAt=" + correction.getPreviousCheckInAt()
                        + ", newCheckInAt=" + correction.getNewCheckInAt()
                        + ", previousCheckOutAt=" + correction.getPreviousCheckOutAt()
                        + ", newCheckOutAt=" + correction.getNewCheckOutAt()
                        + ", reason=" + correction.getReason());
        return response(correction);
    }

    @Transactional(readOnly = true)
    public List<StaffAttendanceCorrectionResponse> history(UUID attendanceId) {
        requireManager();
        if (!attendanceRepository.existsById(attendanceId)) {
            throw new ResourceNotFoundException("Staff Attendance not found.");
        }
        return corrections.findByAttendanceIdOrderByCorrectedAtAsc(attendanceId)
                .stream().map(this::response).toList();
    }

    private void validateRequestShape(StaffAttendance attendance,
            CreateStaffAttendanceCorrectionRequest request) {
        if (request.reason() == null || request.reason().isBlank() || request.reason().trim().length() > 500) {
            throw new IllegalArgumentException("Correction reason is required and must not exceed 500 characters.");
        }
        switch (request.type()) {
            case CHECK_IN_TIME -> requireShape(request.checkInAt() != null && request.checkOutAt() == null,
                    "CHECK_IN_TIME requires only checkInAt.");
            case CHECK_OUT_TIME -> {
                requireStatus(attendance, StaffAttendanceStatus.CLOSED,
                        "CHECK_OUT_TIME is only valid for CLOSED Attendance.");
                requireShape(request.checkInAt() == null && request.checkOutAt() != null,
                        "CHECK_OUT_TIME requires only checkOutAt.");
            }
            case CHECK_IN_AND_OUT -> {
                requireStatus(attendance, StaffAttendanceStatus.CLOSED,
                        "CHECK_IN_AND_OUT is only valid for CLOSED Attendance.");
                requireShape(request.checkInAt() != null && request.checkOutAt() != null,
                        "CHECK_IN_AND_OUT requires checkInAt and checkOutAt.");
            }
            case MISSED_CHECKOUT -> {
                requireStatus(attendance, StaffAttendanceStatus.OPEN,
                        "MISSED_CHECKOUT is only valid for OPEN Attendance.");
                requireShape(request.checkInAt() == null && request.checkOutAt() != null,
                        "MISSED_CHECKOUT requires only checkOutAt.");
            }
        }
    }

    private void validateNotFuture(Instant value) {
        if (value != null && value.isAfter(clock.instant())) {
            throw new IllegalArgumentException("Corrected Attendance timestamps cannot be in the future.");
        }
    }

    private void validateChanged(StaffAttendanceCorrectionType type, Instant previousCheckIn,
            Instant previousCheckOut, Instant nextCheckIn, Instant nextCheckOut) {
        boolean changed = switch (type) {
            case CHECK_IN_TIME -> !Objects.equals(previousCheckIn, nextCheckIn);
            case CHECK_OUT_TIME, MISSED_CHECKOUT -> !Objects.equals(previousCheckOut, nextCheckOut);
            case CHECK_IN_AND_OUT -> !Objects.equals(previousCheckIn, nextCheckIn)
                    || !Objects.equals(previousCheckOut, nextCheckOut);
        };
        if (!changed) throw new ResourceConflictException("Attendance correction does not change the persisted timestamps.");
    }

    private void validateInterval(Instant checkIn, Instant checkOut) {
        if (checkOut != null && !checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException("Corrected check-out time must be later than check-in time.");
        }
    }

    private void populateChangedValues(StaffAttendanceCorrection value,
            StaffAttendanceCorrectionType type, Instant previousCheckIn, Instant previousCheckOut,
            Instant nextCheckIn, Instant nextCheckOut) {
        if (type == StaffAttendanceCorrectionType.CHECK_IN_TIME
                || type == StaffAttendanceCorrectionType.CHECK_IN_AND_OUT) {
            value.setPreviousCheckInAt(previousCheckIn);
            value.setNewCheckInAt(nextCheckIn);
        }
        if (type == StaffAttendanceCorrectionType.CHECK_OUT_TIME
                || type == StaffAttendanceCorrectionType.CHECK_IN_AND_OUT
                || type == StaffAttendanceCorrectionType.MISSED_CHECKOUT) {
            value.setPreviousCheckOutAt(previousCheckOut);
            value.setNewCheckOutAt(nextCheckOut);
        }
    }

    private StaffAttendanceCorrectionResponse response(StaffAttendanceCorrection value) {
        User actor = users.findById(value.getCorrectedByUserId()).orElse(null);
        AttendanceEmployeeResponse safeActor = actor == null ? null
                : new AttendanceEmployeeResponse(actor.getId(), actor.getUsername(), actor.getFullName());
        return new StaffAttendanceCorrectionResponse(value.getId(), value.getAttendanceId(),
                value.getCorrectionType(), value.getPreviousCheckInAt(), value.getNewCheckInAt(),
                value.getPreviousCheckOutAt(), value.getNewCheckOutAt(),
                value.getPreviousWorkedMinutes(), value.getNewWorkedMinutes(), value.getReason(),
                safeActor, value.getCorrectedAt());
    }

    private Long workedMinutes(Instant checkIn, Instant checkOut) {
        return checkOut == null ? null : Duration.between(checkIn, checkOut).toMinutes();
    }

    private void requireManager() {
        Optional<Role> role = currentRoles.getCurrentRole();
        if (role.isEmpty() || !permissions.canManageHr(role.get())) {
            throw new AccessDeniedException("Attendance corrections are restricted to Director or Super Admin.");
        }
    }

    private void requireShape(boolean valid, String message) {
        if (!valid) throw new IllegalArgumentException(message);
    }

    private void requireStatus(StaffAttendance attendance, StaffAttendanceStatus status, String message) {
        if (attendance.getStatus() != status) throw new ResourceConflictException(message);
    }
}
