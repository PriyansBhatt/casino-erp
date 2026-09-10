package com.casino.casinoerp;

import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.exception.ResourceConflictException;
import com.casino.casinoerp.repository.*;
import com.casino.casinoerp.security.Role;
import com.casino.casinoerp.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.*;
import org.springframework.security.access.AccessDeniedException;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StaffLeaveLifecycleServiceTests {
    @Mock StaffLeaveRequestRepository requests;
    @Mock StaffProfileRepository profiles;
    @Mock LeaveTypeRepository leaveTypes;
    @Mock StaffRosterAssignmentRepository rosters;
    @Mock ShiftDefinitionRepository shifts;
    @Mock UserRepository users;
    @Mock AuthenticatedUserService authenticated;
    @Mock CurrentUserRoleService roles;
    @Mock AuditLogService audit;
    StaffLeaveRequestService service;
    User manager;
    User employee;
    StaffProfile staff;
    LeaveType type;
    StaffLeaveRequest leave;
    LocalDateTime expectedNow = LocalDateTime.of(2026, 9, 10, 6, 30);

    @BeforeEach void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new StaffLeaveRequestService(requests, profiles, leaveTypes, rosters, shifts, users,
                authenticated, roles, new RolePermissionService(), audit,
                Clock.fixed(expectedNow.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        manager = user("director", Role.DIRECTOR.name());
        employee = user("cashier", Role.CASHIER.name());
        staff = new StaffProfile(); staff.setId(UUID.randomUUID()); staff.setUserId(employee.getId());
        staff.setEmployeeCode("EMP-001"); staff.setEmploymentStatus(EmploymentStatus.ACTIVE);
        type = new LeaveType(); type.setId(UUID.randomUUID()); type.setCode("ANNUAL");
        type.setName("Annual"); type.setActive(true);
        leave = request(LeaveRequestStatus.PENDING);
        when(roles.getCurrentRole()).thenReturn(Optional.of(Role.DIRECTOR));
        when(authenticated.getRequiredUser()).thenReturn(manager);
        when(requests.findByIdForUpdate(leave.getId())).thenReturn(Optional.of(leave));
        when(requests.findById(leave.getId())).thenReturn(Optional.of(leave));
        when(rosters.findByStaffProfileIdAndStatusAndRosterDateBetweenOrderByRosterDateAsc(
                any(), eq(RosterStatus.SCHEDULED), any(), any())).thenReturn(List.of());
        when(shifts.findAllById(any())).thenReturn(List.of());
        when(requests.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(profiles.findById(staff.getId())).thenReturn(Optional.of(staff));
        when(profiles.findByIdForUpdate(staff.getId())).thenReturn(Optional.of(staff));
        when(profiles.findByUserId(employee.getId())).thenReturn(Optional.of(staff));
        when(leaveTypes.findById(type.getId())).thenReturn(Optional.of(type));
        when(users.findById(employee.getId())).thenReturn(Optional.of(employee));
        when(users.findById(manager.getId())).thenReturn(Optional.of(manager));
        when(users.findAllById(any())).thenAnswer(invocation -> {
            Iterable<UUID> ids = invocation.getArgument(0);
            List<User> result = new ArrayList<>();
            ids.forEach(id -> {
                if (id.equals(employee.getId())) result.add(employee);
                if (id.equals(manager.getId())) result.add(manager);
            });
            return result;
        });
    }

    @Test void pendingApprovalRecordsBackendActorTimeRemarksAndAudit() {
        StaffLeaveRequestResponse result = service.approve(leave.getId(), new ApproveStaffLeaveRequest(" Approved "));
        assertThat(result.status()).isEqualTo(LeaveRequestStatus.APPROVED);
        assertThat(result.reviewedBy().username()).isEqualTo("director");
        assertThat(result.reviewedAt()).isEqualTo(expectedNow);
        assertThat(result.reviewReason()).isEqualTo("Approved");
        verify(requests).findByIdForUpdate(leave.getId());
        verify(audit).log(eq("APPROVE_LEAVE_REQUEST"), eq("HR"), eq(leave.getId()),
                eq(manager.getId()), contains("APPROVED"));
    }

    @Test void inactiveEmployeeAndInactiveLeaveTypeRemainReviewable() {
        staff.setEmploymentStatus(EmploymentStatus.TERMINATED);
        type.setActive(false);
        assertThat(service.approve(leave.getId(), new ApproveStaffLeaveRequest(null)).status())
                .isEqualTo(LeaveRequestStatus.APPROVED);
    }

    @Test void rejectionRequiresPendingAndRecordsReason() {
        StaffLeaveRequestResponse result = service.reject(leave.getId(),
                new RejectStaffLeaveRequest(" Insufficient coverage "));
        assertThat(result.status()).isEqualTo(LeaveRequestStatus.REJECTED);
        assertThat(result.reviewReason()).isEqualTo("Insufficient coverage");
        assertThat(result.reviewedAt()).isEqualTo(expectedNow);
        verify(audit).log(eq("REJECT_LEAVE_REQUEST"), eq("HR"), any(), any(), contains("REJECTED"));
    }

    @Test void employeeCancelsOnlyOwnPendingRequestWithoutRecheckingEmploymentStatus() {
        staff.setEmploymentStatus(EmploymentStatus.INACTIVE);
        when(authenticated.getRequiredUser()).thenReturn(employee);
        StaffLeaveRequestResponse result = service.cancelMine(leave.getId(),
                new CancelStaffLeaveRequest(" Plans changed "));
        assertThat(result.status()).isEqualTo(LeaveRequestStatus.CANCELLED);
        assertThat(result.cancelledBy().username()).isEqualTo("cashier");
        assertThat(result.cancellationReason()).isEqualTo("Plans changed");
    }

    @Test void employeeCannotCancelAnotherEmployeesRequest() {
        when(authenticated.getRequiredUser()).thenReturn(employee);
        leave.setStaffProfileId(UUID.randomUUID());
        assertThatThrownBy(() -> service.cancelMine(leave.getId(), new CancelStaffLeaveRequest("Wrong request")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @ParameterizedTest
    @EnumSource(value = LeaveRequestStatus.class, names = {"APPROVED", "REJECTED", "CANCELLED"})
    void employeeCannotCancelNonPendingRequest(LeaveRequestStatus status) {
        when(authenticated.getRequiredUser()).thenReturn(employee);
        leave.setStatus(status);
        assertThatThrownBy(() -> service.cancelMine(leave.getId(), new CancelStaffLeaveRequest("No longer needed")))
                .isInstanceOf(ResourceConflictException.class).hasMessageContaining(status.name());
    }

    @Test void managementCancelsApprovedAndPreservesReviewMetadata() {
        leave.setStatus(LeaveRequestStatus.APPROVED);
        leave.setReviewedByUserId(manager.getId());
        leave.setReviewedAt(expectedNow.minusDays(1));
        leave.setReviewReason("Approved earlier");
        service.cancelByManagement(leave.getId(), new CancelStaffLeaveRequest("Coverage changed"));
        assertThat(leave.getStatus()).isEqualTo(LeaveRequestStatus.CANCELLED);
        assertThat(leave.getReviewedAt()).isEqualTo(expectedNow.minusDays(1));
        assertThat(leave.getReviewReason()).isEqualTo("Approved earlier");
        assertThat(leave.getCancellationReason()).isEqualTo("Coverage changed");
    }

    @Test void managementCancelsPendingWithoutCreatingFalseReviewMetadata() {
        StaffLeaveRequestResponse result = service.cancelByManagement(leave.getId(),
                new CancelStaffLeaveRequest("Management cancellation"));
        assertThat(result.status()).isEqualTo(LeaveRequestStatus.CANCELLED);
        assertThat(result.reviewedBy()).isNull();
        assertThat(result.reviewedAt()).isNull();
        assertThat(result.reviewReason()).isNull();
        assertThat(result.cancelledBy().username()).isEqualTo("director");
        verify(audit).log(eq("CANCEL_LEAVE_REQUEST"), eq("HR"), any(), any(), contains("CANCELLED"));
    }

    @ParameterizedTest
    @EnumSource(value = LeaveRequestStatus.class, names = {"APPROVED", "REJECTED", "CANCELLED"})
    void approvalReplayAndInvalidTransitionsConflict(LeaveRequestStatus status) {
        leave.setStatus(status);
        assertThatThrownBy(() -> service.approve(leave.getId(), new ApproveStaffLeaveRequest(null)))
                .isInstanceOf(ResourceConflictException.class);
        verify(audit, never()).log(eq("APPROVE_LEAVE_REQUEST"), any(), any(), any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = LeaveRequestStatus.class, names = {"APPROVED", "REJECTED", "CANCELLED"})
    void rejectionInvalidTransitionsConflict(LeaveRequestStatus status) {
        leave.setStatus(status);
        assertThatThrownBy(() -> service.reject(leave.getId(), new RejectStaffLeaveRequest("Denied")))
                .isInstanceOf(ResourceConflictException.class);
    }

    @ParameterizedTest
    @EnumSource(value = LeaveRequestStatus.class, names = {"REJECTED", "CANCELLED"})
    void managementCannotCancelTerminalRequest(LeaveRequestStatus status) {
        leave.setStatus(status);
        assertThatThrownBy(() -> service.cancelByManagement(leave.getId(), new CancelStaffLeaveRequest("Denied")))
                .isInstanceOf(ResourceConflictException.class);
    }

    @Test void rowLockSerializesApproveVersusRejectAndThenAllowsManagementCancellation() {
        service.approve(leave.getId(), new ApproveStaffLeaveRequest(null));
        assertThatThrownBy(() -> service.reject(leave.getId(), new RejectStaffLeaveRequest("Too late")))
                .isInstanceOf(ResourceConflictException.class);
        assertThat(service.cancelByManagement(leave.getId(), new CancelStaffLeaveRequest("Coverage changed")).status())
                .isEqualTo(LeaveRequestStatus.CANCELLED);
        verify(requests, atLeast(3)).findByIdForUpdate(leave.getId());
    }

    @Test void nonManagerCannotUseManagementLifecycle() {
        when(roles.getCurrentRole()).thenReturn(Optional.of(Role.CASHIER));
        assertThatThrownBy(() -> service.approve(leave.getId(), new ApproveStaffLeaveRequest(null)))
                .isInstanceOf(AccessDeniedException.class);
        verify(requests, never()).findByIdForUpdate(any());
    }

    @Test void approvalIsRejectedWhenScheduledRosterOverlapsLeaveAndLeavesRequestPending() {
        ShiftDefinition shift = shift("DAY", LocalTime.of(13, 0), LocalTime.of(23, 0), false);
        StaffRosterAssignment roster = roster(LocalDate.of(2026, 9, 15), shift, RosterStatus.SCHEDULED);
        when(rosters.findByStaffProfileIdAndStatusAndRosterDateBetweenOrderByRosterDateAsc(
                staff.getId(), RosterStatus.SCHEDULED, LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 16)))
                .thenReturn(List.of(roster));
        when(shifts.findAllById(any())).thenReturn(List.of(shift));
        assertThatThrownBy(() -> service.approve(leave.getId(), new ApproveStaffLeaveRequest("Approved")))
                .isInstanceOf(ResourceConflictException.class).hasMessageContaining("scheduled roster");
        assertThat(leave.getStatus()).isEqualTo(LeaveRequestStatus.PENDING);
        verify(requests, never()).save(any());
        verify(audit, never()).log(eq("APPROVE_LEAVE_REQUEST"), any(), any(), any(), any());
    }

    @Test void previousDayOvernightRosterOverlapsLeaveBeginningAfterMidnight() {
        ShiftDefinition shift = shift("NIGHT", LocalTime.of(18, 0), LocalTime.of(3, 30), true);
        StaffRosterAssignment roster = roster(leave.getStartDate().minusDays(1), shift, RosterStatus.SCHEDULED);
        when(rosters.findByStaffProfileIdAndStatusAndRosterDateBetweenOrderByRosterDateAsc(any(), any(), any(), any()))
                .thenReturn(List.of(roster));
        when(shifts.findAllById(any())).thenReturn(List.of(shift));
        assertThatThrownBy(() -> service.approve(leave.getId(), new ApproveStaffLeaveRequest(null)))
                .isInstanceOf(ResourceConflictException.class);
    }

    @Test void nonOverlappingOrCancelledRosterDoesNotBlockApproval() {
        ShiftDefinition shift = shift("DAY", LocalTime.of(13, 0), LocalTime.of(23, 0), false);
        StaffRosterAssignment cancelled = roster(LocalDate.of(2026, 9, 15), shift, RosterStatus.CANCELLED);
        when(rosters.findByStaffProfileIdAndStatusAndRosterDateBetweenOrderByRosterDateAsc(any(), any(), any(), any()))
                .thenReturn(List.of(cancelled));
        when(shifts.findAllById(any())).thenReturn(List.of(shift));
        assertThat(service.approve(leave.getId(), new ApproveStaffLeaveRequest(null)).status())
                .isEqualTo(LeaveRequestStatus.APPROVED);
    }

    @Test void scheduledRosterOutsideLeaveIntervalDoesNotBlockApproval() {
        ShiftDefinition shift = shift("DAY", LocalTime.of(13, 0), LocalTime.of(23, 0), false);
        StaffRosterAssignment roster = roster(leave.getStartDate().minusDays(1), shift, RosterStatus.SCHEDULED);
        when(rosters.findByStaffProfileIdAndStatusAndRosterDateBetweenOrderByRosterDateAsc(any(), any(), any(), any()))
                .thenReturn(List.of(roster));
        when(shifts.findAllById(any())).thenReturn(List.of(shift));
        assertThat(service.approve(leave.getId(), new ApproveStaffLeaveRequest(null)).status())
                .isEqualTo(LeaveRequestStatus.APPROVED);
    }

    @Test void cancellingConflictingRosterAllowsLaterApprovalAndLocksStaffBeforeLeave() {
        ShiftDefinition shift = shift("DAY", LocalTime.of(13, 0), LocalTime.of(23, 0), false);
        StaffRosterAssignment roster = roster(LocalDate.of(2026, 9, 15), shift, RosterStatus.SCHEDULED);
        when(rosters.findByStaffProfileIdAndStatusAndRosterDateBetweenOrderByRosterDateAsc(any(), any(), any(), any()))
                .thenReturn(List.of(roster));
        when(shifts.findAllById(any())).thenReturn(List.of(shift));
        assertThatThrownBy(() -> service.approve(leave.getId(), new ApproveStaffLeaveRequest(null)))
                .isInstanceOf(ResourceConflictException.class);
        roster.setStatus(RosterStatus.CANCELLED);
        assertThat(service.approve(leave.getId(), new ApproveStaffLeaveRequest(null)).status())
                .isEqualTo(LeaveRequestStatus.APPROVED);
        InOrder locks = inOrder(profiles, requests);
        locks.verify(profiles, atLeastOnce()).findByIdForUpdate(staff.getId());
        locks.verify(requests, atLeastOnce()).findByIdForUpdate(leave.getId());
    }

    private StaffLeaveRequest request(LeaveRequestStatus status) {
        StaffLeaveRequest value = new StaffLeaveRequest(); value.setId(UUID.randomUUID());
        value.setStaffProfileId(staff.getId()); value.setLeaveTypeId(type.getId());
        value.setStartDate(LocalDate.of(2026, 9, 14)); value.setEndDate(LocalDate.of(2026, 9, 16));
        value.setReason("Family leave"); value.setStatus(status);
        value.setSubmittedAt(expectedNow.minusDays(2)); value.setCreatedAt(expectedNow.minusDays(2));
        value.setUpdatedAt(expectedNow.minusDays(2)); return value;
    }
    private User user(String username, String role) { User value = new User(); value.setId(UUID.randomUUID());
        value.setUsername(username); value.setFullName(username); value.setRole(role); return value; }
    private ShiftDefinition shift(String code, LocalTime start, LocalTime end, boolean crossesMidnight) {
        ShiftDefinition value = new ShiftDefinition(); value.setId(UUID.randomUUID()); value.setCode(code);
        value.setName(code); value.setStartTime(start); value.setEndTime(end);
        value.setCrossesMidnight(crossesMidnight); value.setActive(true); return value;
    }
    private StaffRosterAssignment roster(LocalDate rosterDate, ShiftDefinition shift, RosterStatus status) {
        StaffRosterAssignment value = new StaffRosterAssignment(); value.setId(UUID.randomUUID());
        value.setStaffProfileId(staff.getId()); value.setShiftDefinitionId(shift.getId());
        value.setRosterDate(rosterDate); value.setStatus(status); return value;
    }
}
