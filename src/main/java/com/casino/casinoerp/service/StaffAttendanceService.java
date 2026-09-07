package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.AttendanceEmployeeResponse;
import com.casino.casinoerp.dto.StaffAttendanceResponse;
import com.casino.casinoerp.entity.StaffAttendance;
import com.casino.casinoerp.entity.StaffAttendanceStatus;
import com.casino.casinoerp.entity.User;
import com.casino.casinoerp.exception.ResourceConflictException;
import com.casino.casinoerp.repository.StaffAttendanceRepository;
import com.casino.casinoerp.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;
import java.util.UUID;

@Service
public class StaffAttendanceService {
    static final ZoneId CASINO_ZONE = ZoneId.of("Asia/Kathmandu");

    private final StaffAttendanceRepository attendanceRepository;
    private final UserRepository userRepository;
    private final AuthenticatedUserService authenticatedUsers;
    private final BusinessDateService businessDates;
    private final AuditLogService audit;
    private final Clock clock;

    @Autowired
    public StaffAttendanceService(
            StaffAttendanceRepository attendanceRepository,
            UserRepository userRepository,
            AuthenticatedUserService authenticatedUsers,
            BusinessDateService businessDates,
            AuditLogService audit) {
        this(attendanceRepository, userRepository, authenticatedUsers, businessDates, audit,
                Clock.system(CASINO_ZONE));
    }

    public StaffAttendanceService(
            StaffAttendanceRepository attendanceRepository,
            UserRepository userRepository,
            AuthenticatedUserService authenticatedUsers,
            BusinessDateService businessDates,
            AuditLogService audit,
            Clock clock) {
        this.attendanceRepository = attendanceRepository;
        this.userRepository = userRepository;
        this.authenticatedUsers = authenticatedUsers;
        this.businessDates = businessDates;
        this.audit = audit;
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
        LocalDateTime casinoTime = LocalDateTime.ofInstant(now, CASINO_ZONE);
        StaffAttendance attendance = new StaffAttendance();
        attendance.setId(UUID.randomUUID());
        attendance.setUser(user);
        attendance.setBusinessDate(businessDates.resolveBusinessDate(casinoTime));
        attendance.setStatus(StaffAttendanceStatus.OPEN);
        attendance.setCheckInAt(now);
        attendance.setCreatedAt(now);
        attendance.setUpdatedAt(now);
        try {
            StaffAttendance saved = attendanceRepository.saveAndFlush(attendance);
            audit.log("STAFF_ATTENDANCE_CHECK_IN", "STAFF_ATTENDANCE", saved.getId(), user.getId(),
                    "Staff attendance checked in for businessDate=" + saved.getBusinessDate());
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
                attendance.getCheckOutAt(), workedMinutes(attendance), attendance.getCreatedAt(),
                attendance.getUpdatedAt());
    }

    private Long workedMinutes(StaffAttendance attendance) {
        return attendance.getCheckOutAt() == null ? null
                : Duration.between(attendance.getCheckInAt(), attendance.getCheckOutAt()).toMinutes();
    }
}
