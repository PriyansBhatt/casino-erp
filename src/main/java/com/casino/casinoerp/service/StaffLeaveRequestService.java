package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.exception.*;
import com.casino.casinoerp.repository.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class StaffLeaveRequestService {
    private static final List<LeaveRequestStatus> BLOCKING_STATUSES =
            List.of(LeaveRequestStatus.PENDING, LeaveRequestStatus.APPROVED);

    private final StaffLeaveRequestRepository requests;
    private final StaffProfileRepository profiles;
    private final LeaveTypeRepository leaveTypes;
    private final StaffRosterAssignmentRepository rosters;
    private final ShiftDefinitionRepository shifts;
    private final UserRepository users;
    private final AuthenticatedUserService authenticatedUsers;
    private final CurrentUserRoleService currentRoles;
    private final RolePermissionService permissions;
    private final AuditLogService audit;
    private final Clock clock;

    public StaffLeaveRequestService(StaffLeaveRequestRepository requests,
            StaffProfileRepository profiles, LeaveTypeRepository leaveTypes,
            StaffRosterAssignmentRepository rosters, ShiftDefinitionRepository shifts, UserRepository users,
            AuthenticatedUserService authenticatedUsers, CurrentUserRoleService currentRoles,
            RolePermissionService permissions, AuditLogService audit, Clock clock) {
        this.requests = requests;
        this.profiles = profiles;
        this.leaveTypes = leaveTypes;
        this.rosters = rosters;
        this.shifts = shifts;
        this.users = users;
        this.authenticatedUsers = authenticatedUsers;
        this.currentRoles = currentRoles;
        this.permissions = permissions;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public StaffLeaveRequestResponse create(CreateStaffLeaveRequest request) {
        validateDates(request.startDate(), request.endDate());
        User actor = authenticatedUsers.getRequiredUser();
        StaffProfile linked = profiles.findByUserId(actor.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Authenticated user does not have a Staff Profile."));
        StaffProfile staff = profiles.findByIdForUpdate(linked.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Staff Profile not found."));
        if (staff.getEmploymentStatus() != EmploymentStatus.ACTIVE) {
            throw new IllegalArgumentException("Only an ACTIVE Staff Profile may submit a Leave Request.");
        }
        LeaveType leaveType = leaveTypes.findById(request.leaveTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Leave Type not found."));
        if (!leaveType.isActive()) {
            throw new IllegalArgumentException("Leave Type must be ACTIVE for a new request.");
        }
        if (!requests.findOverlapping(staff.getId(), BLOCKING_STATUSES,
                request.startDate(), request.endDate()).isEmpty()) {
            throw new ResourceConflictException(
                    "Leave Request overlaps an existing PENDING or APPROVED request.");
        }

        LocalDateTime now = now();
        StaffLeaveRequest value = new StaffLeaveRequest();
        value.setStaffProfileId(staff.getId());
        value.setLeaveTypeId(leaveType.getId());
        value.setStartDate(request.startDate());
        value.setEndDate(request.endDate());
        value.setReason(request.reason().trim());
        value.setStatus(LeaveRequestStatus.PENDING);
        value.setSubmittedAt(now);
        value.setCreatedAt(now);
        value.setUpdatedAt(now);
        value = requests.saveAndFlush(value);
        audit.log("CREATE_LEAVE_REQUEST", "HR", value.getId(), actor.getId(),
                "StaffProfile=" + staff.getId() + ", Leave Type=" + leaveType.getCode()
                        + ", dates=" + value.getStartDate() + " to " + value.getEndDate());
        return response(value);
    }

    @Transactional(readOnly = true)
    public List<StaffLeaveRequestResponse> mine(LeaveRequestStatus status,
            LocalDate startDate, LocalDate endDate) {
        validateOptionalDates(startDate, endDate);
        User actor = authenticatedUsers.getRequiredUser();
        StaffProfile staff = profiles.findByUserId(actor.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Authenticated user does not have a Staff Profile."));
        return responses(requests.search(staff.getId(), null, null, status, startDate, endDate));
    }

    @Transactional(readOnly = true)
    public List<StaffLeaveRequestResponse> list(LocalDate startDate, LocalDate endDate,
            UUID staffProfileId, UUID departmentId, UUID leaveTypeId, LeaveRequestStatus status) {
        requireManager();
        validateOptionalDates(startDate, endDate);
        return responses(requests.search(staffProfileId, departmentId, leaveTypeId,
                status, startDate, endDate));
    }

    @Transactional(readOnly = true)
    public StaffLeaveRequestResponse get(UUID id) {
        requireManager();
        return response(requests.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave Request not found.")));
    }

    @Transactional
    public StaffLeaveRequestResponse approve(UUID id, ApproveStaffLeaveRequest request) {
        requireManager();
        User actor = authenticatedUsers.getRequiredUser();
        StaffLeaveRequest preview = requests.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave Request not found."));
        profiles.findByIdForUpdate(preview.getStaffProfileId())
                .orElseThrow(() -> new ResourceNotFoundException("Staff Profile not found."));
        StaffLeaveRequest value = locked(id);
        requireStatus(value, LeaveRequestStatus.PENDING, "approve");
        validateScheduledRoster(value);
        LocalDateTime now = now();
        value.setStatus(LeaveRequestStatus.APPROVED);
        value.setReviewedByUserId(actor.getId());
        value.setReviewedAt(now);
        value.setReviewReason(optionalText(request.remarks()));
        value.setUpdatedAt(now);
        value = requests.save(value);
        auditLifecycle("APPROVE_LEAVE_REQUEST", value, actor, value.getReviewReason());
        return response(value);
    }

    @Transactional
    public StaffLeaveRequestResponse reject(UUID id, RejectStaffLeaveRequest request) {
        requireManager();
        User actor = authenticatedUsers.getRequiredUser();
        StaffLeaveRequest value = locked(id);
        requireStatus(value, LeaveRequestStatus.PENDING, "reject");
        LocalDateTime now = now();
        value.setStatus(LeaveRequestStatus.REJECTED);
        value.setReviewedByUserId(actor.getId());
        value.setReviewedAt(now);
        value.setReviewReason(request.reason().trim());
        value.setUpdatedAt(now);
        value = requests.save(value);
        auditLifecycle("REJECT_LEAVE_REQUEST", value, actor, value.getReviewReason());
        return response(value);
    }

    @Transactional
    public StaffLeaveRequestResponse cancelByManagement(UUID id, CancelStaffLeaveRequest request) {
        requireManager();
        User actor = authenticatedUsers.getRequiredUser();
        StaffLeaveRequest value = locked(id);
        if (value.getStatus() != LeaveRequestStatus.PENDING
                && value.getStatus() != LeaveRequestStatus.APPROVED) {
            throw transitionConflict(value, "cancel");
        }
        cancel(value, actor, request.reason());
        auditLifecycle("CANCEL_LEAVE_REQUEST", value, actor, value.getCancellationReason());
        return response(value);
    }

    @Transactional
    public StaffLeaveRequestResponse cancelMine(UUID id, CancelStaffLeaveRequest request) {
        User actor = authenticatedUsers.getRequiredUser();
        StaffProfile staff = profiles.findByUserId(actor.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Authenticated user does not have a Staff Profile."));
        StaffLeaveRequest value = locked(id);
        if (!value.getStaffProfileId().equals(staff.getId())) {
            throw new AccessDeniedException("Employees may cancel only their own Leave Requests.");
        }
        requireStatus(value, LeaveRequestStatus.PENDING, "cancel");
        cancel(value, actor, request.reason());
        auditLifecycle("CANCEL_LEAVE_REQUEST", value, actor, value.getCancellationReason());
        return response(value);
    }

    private void cancel(StaffLeaveRequest value, User actor, String reason) {
        LocalDateTime now = now();
        value.setStatus(LeaveRequestStatus.CANCELLED);
        value.setCancelledByUserId(actor.getId());
        value.setCancelledAt(now);
        value.setCancellationReason(reason.trim());
        value.setUpdatedAt(now);
        requests.save(value);
    }

    private StaffLeaveRequest locked(UUID id) {
        return requests.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave Request not found."));
    }

    private void requireStatus(StaffLeaveRequest value, LeaveRequestStatus expected, String action) {
        if (value.getStatus() != expected) throw transitionConflict(value, action);
    }

    private ResourceConflictException transitionConflict(StaffLeaveRequest value, String action) {
        return new ResourceConflictException("Leave Request in status " + value.getStatus()
                + " cannot be " + action + "d.");
    }

    private void auditLifecycle(String action, StaffLeaveRequest value, User actor, String explanation) {
        LeaveType type = leaveTypes.findById(value.getLeaveTypeId()).orElse(null);
        audit.log(action, "HR", value.getId(), actor.getId(),
                "StaffProfile=" + value.getStaffProfileId()
                        + ", Leave Type=" + (type == null ? value.getLeaveTypeId() : type.getCode())
                        + ", dates=" + value.getStartDate() + " to " + value.getEndDate()
                        + ", status=" + value.getStatus()
                        + (explanation == null ? "" : ", reason=" + explanation));
    }

    private void validateScheduledRoster(StaffLeaveRequest leave) {
        List<StaffRosterAssignment> candidates = rosters
                .findByStaffProfileIdAndStatusAndRosterDateBetweenOrderByRosterDateAsc(
                        leave.getStaffProfileId(), RosterStatus.SCHEDULED,
                        leave.getStartDate().minusDays(1), leave.getEndDate());
        Set<UUID> shiftIds = new HashSet<>();
        candidates.forEach(roster -> shiftIds.add(roster.getShiftDefinitionId()));
        Map<UUID, ShiftDefinition> shiftById = new HashMap<>();
        shifts.findAllById(shiftIds).forEach(shift -> shiftById.put(shift.getId(), shift));
        ZonedDateTime leaveStart = leave.getStartDate().atStartOfDay(ZoneId.of("Asia/Kathmandu"));
        ZonedDateTime leaveEnd = leave.getEndDate().plusDays(1).atStartOfDay(ZoneId.of("Asia/Kathmandu"));
        for (StaffRosterAssignment roster : candidates) {
            if (roster.getStatus() != RosterStatus.SCHEDULED) continue;
            ShiftDefinition shift = shiftById.get(roster.getShiftDefinitionId());
            if (shift == null) throw new IllegalStateException("Roster references a missing Shift Definition.");
            ZonedDateTime rosterStart = roster.getRosterDate().atTime(shift.getStartTime())
                    .atZone(ZoneId.of("Asia/Kathmandu"));
            ZonedDateTime rosterEnd = (shift.isCrossesMidnight()
                    ? roster.getRosterDate().plusDays(1) : roster.getRosterDate())
                    .atTime(shift.getEndTime()).atZone(ZoneId.of("Asia/Kathmandu"));
            if (rosterStart.isBefore(leaveEnd) && leaveStart.isBefore(rosterEnd)) {
                throw new ResourceConflictException(
                        "Leave cannot be approved because scheduled roster assignments conflict with the leave period."
                                + " Roster date=" + roster.getRosterDate() + ", shift=" + shift.getCode() + ".");
            }
        }
    }

    private StaffLeaveRequestResponse response(StaffLeaveRequest value) {
        return response(value, lifecycleActors(List.of(value)));
    }

    private StaffLeaveRequestResponse response(StaffLeaveRequest value, Map<UUID, User> lifecycleActors) {
        StaffProfile staff = profiles.findById(value.getStaffProfileId()).orElse(null);
        User user = staff == null ? null : users.findById(staff.getUserId()).orElse(null);
        LeaveType leaveType = leaveTypes.findById(value.getLeaveTypeId()).orElse(null);
        StaffLeaveRequestResponse.StaffSummary staffSummary = staff == null ? null
                : new StaffLeaveRequestResponse.StaffSummary(staff.getId(), staff.getUserId(),
                        staff.getEmployeeCode(), user == null ? null : user.getUsername(),
                        user == null ? null : user.getFullName());
        StaffLeaveRequestResponse.LeaveTypeSummary typeSummary = leaveType == null ? null
                : new StaffLeaveRequestResponse.LeaveTypeSummary(
                        leaveType.getId(), leaveType.getCode(), leaveType.getName());
        return new StaffLeaveRequestResponse(value.getId(), staffSummary, typeSummary,
                value.getStartDate(), value.getEndDate(), calendarDays(value.getStartDate(), value.getEndDate()),
                value.getReason(), value.getStatus(), actor(value.getReviewedByUserId(), lifecycleActors), value.getReviewedAt(),
                value.getReviewReason(), actor(value.getCancelledByUserId(), lifecycleActors), value.getCancelledAt(),
                value.getCancellationReason(), value.getSubmittedAt(),
                value.getCreatedAt(), value.getUpdatedAt());
    }

    private StaffLeaveRequestResponse.ActorSummary actor(UUID id, Map<UUID, User> actors) {
        if (id == null) return null;
        User user = actors.get(id);
        return user == null ? null : new StaffLeaveRequestResponse.ActorSummary(
                user.getId(), user.getUsername(), user.getFullName());
    }

    private List<StaffLeaveRequestResponse> responses(List<StaffLeaveRequest> values) {
        Map<UUID, User> actors = lifecycleActors(values);
        return values.stream().map(value -> response(value, actors)).toList();
    }

    private Map<UUID, User> lifecycleActors(Collection<StaffLeaveRequest> values) {
        Set<UUID> ids = new HashSet<>();
        for (StaffLeaveRequest value : values) {
            if (value.getReviewedByUserId() != null) ids.add(value.getReviewedByUserId());
            if (value.getCancelledByUserId() != null) ids.add(value.getCancelledByUserId());
        }
        if (ids.isEmpty()) return Map.of();
        Map<UUID, User> result = new HashMap<>();
        users.findAllById(ids).forEach(user -> result.put(user.getId(), user));
        return result;
    }

    private long calendarDays(LocalDate start, LocalDate end) {
        return ChronoUnit.DAYS.between(start, end) + 1;
    }

    private void validateDates(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) throw new IllegalArgumentException("Leave end date cannot be before start date.");
    }

    private void validateOptionalDates(LocalDate start, LocalDate end) {
        if (start != null && end != null) validateDates(start, end);
    }

    private void requireManager() {
        if (!currentRoles.getCurrentRole().map(permissions::canManageHr).orElse(false)) {
            throw new AccessDeniedException("Leave management is restricted to Director or Super Admin.");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private String optionalText(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
