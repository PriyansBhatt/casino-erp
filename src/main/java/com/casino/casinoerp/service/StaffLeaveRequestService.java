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
    private final UserRepository users;
    private final AuthenticatedUserService authenticatedUsers;
    private final CurrentUserRoleService currentRoles;
    private final RolePermissionService permissions;
    private final AuditLogService audit;

    public StaffLeaveRequestService(StaffLeaveRequestRepository requests,
            StaffProfileRepository profiles, LeaveTypeRepository leaveTypes, UserRepository users,
            AuthenticatedUserService authenticatedUsers, CurrentUserRoleService currentRoles,
            RolePermissionService permissions, AuditLogService audit) {
        this.requests = requests;
        this.profiles = profiles;
        this.leaveTypes = leaveTypes;
        this.users = users;
        this.authenticatedUsers = authenticatedUsers;
        this.currentRoles = currentRoles;
        this.permissions = permissions;
        this.audit = audit;
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

        LocalDateTime now = LocalDateTime.now();
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

    private StaffLeaveRequestResponse response(StaffLeaveRequest value) {
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
                value.getReason(), value.getStatus(), value.getSubmittedAt(),
                value.getCreatedAt(), value.getUpdatedAt());
    }

    private List<StaffLeaveRequestResponse> responses(List<StaffLeaveRequest> values) {
        return values.stream().map(this::response).toList();
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
}
