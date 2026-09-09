package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.AttendanceEmployeeResponse;
import com.casino.casinoerp.dto.StaffAttendanceResponse;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.exception.ResourceConflictException;
import com.casino.casinoerp.repository.StaffAttendanceRepository;
import com.casino.casinoerp.repository.UserRepository;
import com.casino.casinoerp.repository.StaffProfileRepository;
import com.casino.casinoerp.repository.StaffRosterAssignmentRepository;
import com.casino.casinoerp.repository.ShiftDefinitionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class StaffAttendanceService {
    static final ZoneId CASINO_ZONE = ZoneId.of("Asia/Kathmandu");

    private final StaffAttendanceRepository attendanceRepository;
    private final UserRepository userRepository;
    private final AuthenticatedUserService authenticatedUsers;
    private final BusinessDateService businessDates;
    private final AuditLogService audit;
    private final StaffProfileRepository staffProfiles;
    private final StaffRosterAssignmentRepository rosters;
    private final ShiftDefinitionRepository shifts;
    private final Clock clock;

    @Autowired
    public StaffAttendanceService(
            StaffAttendanceRepository attendanceRepository,
            UserRepository userRepository,
            AuthenticatedUserService authenticatedUsers,
            BusinessDateService businessDates,
            AuditLogService audit,
            StaffProfileRepository staffProfiles,
            StaffRosterAssignmentRepository rosters,
            ShiftDefinitionRepository shifts) {
        this(attendanceRepository, userRepository, authenticatedUsers, businessDates, audit,
                staffProfiles, rosters, shifts,
                Clock.system(CASINO_ZONE));
    }

    public StaffAttendanceService(
            StaffAttendanceRepository attendanceRepository,
            UserRepository userRepository,
            AuthenticatedUserService authenticatedUsers,
            BusinessDateService businessDates,
            AuditLogService audit,
            StaffProfileRepository staffProfiles,
            StaffRosterAssignmentRepository rosters,
            ShiftDefinitionRepository shifts,
            Clock clock) {
        this.attendanceRepository = attendanceRepository;
        this.userRepository = userRepository;
        this.authenticatedUsers = authenticatedUsers;
        this.businessDates = businessDates;
        this.audit = audit;
        this.staffProfiles = staffProfiles;
        this.rosters = rosters;
        this.shifts = shifts;
        this.clock = clock;
    }

    @Transactional
    public StaffAttendanceResponse checkIn() {
        User principal = authenticatedUsers.getRequiredUser();
        User user = userRepository.findByIdForUpdate(principal.getId())
                .orElseThrow(() -> new IllegalStateException("Authenticated user account was not found."));
        requireActive(user);
        if (attendanceRepository.findFirstByUserIdAndStatusOrderByCheckInAtDesc(
                user.getId(), StaffAttendanceStatus.OPEN).isPresent()) {
            throw new ResourceConflictException("Employee already has an OPEN attendance shift.");
        }

        Instant now = clock.instant();
        StaffAttendance attendance = new StaffAttendance();
        attendance.setId(UUID.randomUUID());
        attendance.setUser(user);
        attendance.setBusinessDate(businessDates.resolveAttendanceBusinessDate(now));
        attendance.setStatus(StaffAttendanceStatus.OPEN);
        attendance.setCheckInAt(now);
        matchRoster(user, now).ifPresent(match -> applyRosterSnapshot(attendance, match));
        attendance.setCreatedAt(now);
        attendance.setUpdatedAt(now);
        try {
            StaffAttendance saved = attendanceRepository.saveAndFlush(attendance);
            audit.log("STAFF_ATTENDANCE_CHECK_IN", "STAFF_ATTENDANCE", saved.getId(), user.getId(),
                    "Staff attendance checked in for businessDate=" + saved.getBusinessDate()
                            + rosterAuditContext(saved));
            return response(saved);
        } catch (DataIntegrityViolationException exception) {
            throw new ResourceConflictException("Employee already has an OPEN attendance shift.");
        }
    }

    @Transactional
    public StaffAttendanceResponse checkOut() {
        User principal = authenticatedUsers.getRequiredUser();
        User user = userRepository.findByIdForUpdate(principal.getId())
                .orElseThrow(() -> new IllegalStateException("Authenticated user account was not found."));
        StaffAttendance attendance = attendanceRepository.findOpenByUserIdForUpdate(
                        user.getId(), StaffAttendanceStatus.OPEN)
                .orElseThrow(() -> new ResourceConflictException("Employee has no OPEN attendance shift."));
        Instant now = clock.instant();
        if (!now.isAfter(attendance.getCheckInAt())) {
            throw new ResourceConflictException("Check-out time must be later than check-in time.");
        }
        attendance.setStatus(StaffAttendanceStatus.CLOSED);
        attendance.setCheckOutAt(now);
        attendance.setUpdatedAt(now);
        StaffAttendance saved = attendanceRepository.save(attendance);
        audit.log("STAFF_ATTENDANCE_CHECK_OUT", "STAFF_ATTENDANCE", saved.getId(), user.getId(),
                "Staff attendance checked out for businessDate=" + saved.getBusinessDate()
                        + ", workedMinutes=" + workedMinutes(saved));
        return response(saved);
    }

    @Transactional(readOnly = true)
    public StaffAttendanceResponse current() {
        User user = authenticatedUsers.getRequiredUser();
        return attendanceRepository.findFirstByUserIdAndStatusOrderByCheckInAtDesc(
                        user.getId(), StaffAttendanceStatus.OPEN)
                .map(this::response).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<StaffAttendanceResponse> myHistory(LocalDate businessDate) {
        User user = authenticatedUsers.getRequiredUser();
        List<StaffAttendance> values = businessDate == null
                ? attendanceRepository.findByUserIdOrderByCheckInAtDesc(user.getId())
                : attendanceRepository.findByUserIdAndBusinessDateOrderByCheckInAtDesc(
                        user.getId(), businessDate);
        return values.stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public List<StaffAttendanceResponse> report(LocalDate businessDate) {
        requireManagementRole();
        return attendanceRepository.findByBusinessDateOrderByCheckInAtAsc(businessDate)
                .stream().map(this::response).toList();
    }

    private void requireActive(User user) {
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new AccessDeniedException("Only ACTIVE users may start an attendance shift.");
        }
    }

    private void requireManagementRole() {
        String role = authenticatedUsers.getRequiredUser().getRole();
        if (!"DIRECTOR".equalsIgnoreCase(role) && !"SUPER_ADMIN".equalsIgnoreCase(role)) {
            throw new AccessDeniedException("Attendance reports are restricted to Director or Super Admin.");
        }
    }

    private StaffAttendanceResponse response(StaffAttendance attendance) {
        User user = attendance.getUser();
        return new StaffAttendanceResponse(
                attendance.getId(),
                new AttendanceEmployeeResponse(user.getId(), user.getUsername(), user.getFullName()),
                attendance.getBusinessDate(), attendance.getStatus(), attendance.getCheckInAt(),
                attendance.getCheckOutAt(), workedMinutes(attendance), attendance.getRosterAssignmentId(),
                attendance.getRosterAssignmentId() != null, attendance.getShiftCode(), attendance.getShiftName(),
                attendance.getRosterDate(), attendance.getScheduledStartAt(), attendance.getScheduledEndAt(),
                scheduleStatus(attendance), lateByMinutes(attendance), earlyDepartureMinutes(attendance), attendance.getCreatedAt(),
                attendance.getUpdatedAt());
    }

    private Optional<RosterMatch> matchRoster(User user, Instant checkIn) {
        Optional<StaffProfile> staff = staffProfiles.findByUserId(user.getId());
        if (staff.isEmpty()) return Optional.empty();
        LocalDate localDate = checkIn.atZone(CASINO_ZONE).toLocalDate();
        List<RosterMatch> matches = rosters
                .findByStaffProfileIdAndStatusAndRosterDateBetweenOrderByRosterDateAsc(
                        staff.get().getId(), RosterStatus.SCHEDULED, localDate.minusDays(1), localDate.plusDays(1))
                .stream()
                .map(roster -> new RosterMatch(roster, shifts.findById(roster.getShiftDefinitionId())
                        .orElseThrow(() -> new IllegalStateException("Roster references a missing Shift Definition."))))
                .filter(match -> match.roster().getStatus() == RosterStatus.SCHEDULED)
                .filter(match -> !checkIn.isBefore(match.matchStart()) && checkIn.isBefore(match.scheduledEnd()))
                .toList();
        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
    }

    private void applyRosterSnapshot(StaffAttendance attendance, RosterMatch match) {
        attendance.setRosterAssignmentId(match.roster().getId());
        attendance.setRosterDate(match.roster().getRosterDate());
        attendance.setShiftCode(match.shift().getCode());
        attendance.setShiftName(match.shift().getName());
        attendance.setScheduledStartAt(match.scheduledStart());
        attendance.setScheduledEndAt(match.scheduledEnd());
        attendance.setRosterLateGraceMinutes(match.shift().getLateGraceMinutes());
        attendance.setRosterEarlyCheckInMinutes(match.shift().getEarlyCheckInMinutes());
    }

    private AttendanceScheduleStatus scheduleStatus(StaffAttendance attendance) {
        if (attendance.getRosterAssignmentId() == null) return AttendanceScheduleStatus.UNSCHEDULED;
        boolean late = attendance.getCheckInAt().isAfter(
                attendance.getScheduledStartAt().plusSeconds(attendance.getRosterLateGraceMinutes() * 60L));
        boolean early = attendance.getCheckOutAt() != null
                && attendance.getCheckOutAt().isBefore(attendance.getScheduledEndAt());
        if (late && early) return AttendanceScheduleStatus.LATE_AND_EARLY_DEPARTURE;
        if (late) return AttendanceScheduleStatus.LATE;
        if (early) return AttendanceScheduleStatus.EARLY_DEPARTURE;
        return AttendanceScheduleStatus.ON_TIME;
    }

    private long lateByMinutes(StaffAttendance attendance) {
        if (attendance.getScheduledStartAt() == null || !attendance.getCheckInAt().isAfter(attendance.getScheduledStartAt())) return 0;
        return Duration.between(attendance.getScheduledStartAt(), attendance.getCheckInAt()).toMinutes();
    }

    private Long earlyDepartureMinutes(StaffAttendance attendance) {
        if (attendance.getScheduledEndAt() == null || attendance.getCheckOutAt() == null) return null;
        if (!attendance.getCheckOutAt().isBefore(attendance.getScheduledEndAt())) return 0L;
        return Duration.between(attendance.getCheckOutAt(), attendance.getScheduledEndAt()).toMinutes();
    }

    private String rosterAuditContext(StaffAttendance attendance) {
        return attendance.getRosterAssignmentId() == null ? ", roster=UNSCHEDULED"
                : ", rosterAssignmentId=" + attendance.getRosterAssignmentId()
                + ", shift=" + attendance.getShiftCode()
                + ", scheduledStartAt=" + attendance.getScheduledStartAt()
                + ", scheduledEndAt=" + attendance.getScheduledEndAt();
    }

    private record RosterMatch(StaffRosterAssignment roster, ShiftDefinition shift) {
        private Instant scheduledStart() {
            return roster.getRosterDate().atTime(shift.getStartTime()).atZone(CASINO_ZONE).toInstant();
        }
        private Instant scheduledEnd() {
            LocalDate endDate = shift.isCrossesMidnight() ? roster.getRosterDate().plusDays(1) : roster.getRosterDate();
            return endDate.atTime(shift.getEndTime()).atZone(CASINO_ZONE).toInstant();
        }
        private Instant matchStart() {
            return scheduledStart().minusSeconds(shift.getEarlyCheckInMinutes() * 60L);
        }
    }

    private Long workedMinutes(StaffAttendance attendance) {
        return attendance.getCheckOutAt() == null ? null
                : Duration.between(attendance.getCheckInAt(), attendance.getCheckOutAt()).toMinutes();
    }
}
