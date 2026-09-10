package com.casino.casinoerp;

import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.exception.*;
import com.casino.casinoerp.repository.*;
import com.casino.casinoerp.security.Role;
import com.casino.casinoerp.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StaffLeaveRequestServiceTests {
    private final StaffLeaveRequestRepository requests = mock(StaffLeaveRequestRepository.class);
    private final StaffProfileRepository profiles = mock(StaffProfileRepository.class);
    private final LeaveTypeRepository leaveTypes = mock(LeaveTypeRepository.class);
    private final StaffRosterAssignmentRepository rosters = mock(StaffRosterAssignmentRepository.class);
    private final ShiftDefinitionRepository shifts = mock(ShiftDefinitionRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AuthenticatedUserService authenticated = mock(AuthenticatedUserService.class);
    private final CurrentUserRoleService roles = mock(CurrentUserRoleService.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final User employee = user("employee", "CASHIER");
    private final StaffProfile staff = staff(EmploymentStatus.ACTIVE);
    private final LeaveType annual = leaveType(true);
    private StaffLeaveRequestService service;

    @BeforeEach void setUp() {
        service = new StaffLeaveRequestService(requests, profiles, leaveTypes, rosters, shifts, users,
                authenticated, roles, new RolePermissionService(), audit, Clock.systemUTC());
        when(authenticated.getRequiredUser()).thenReturn(employee);
        when(profiles.findByUserId(employee.getId())).thenReturn(Optional.of(staff));
        when(profiles.findByIdForUpdate(staff.getId())).thenReturn(Optional.of(staff));
        when(profiles.findById(staff.getId())).thenReturn(Optional.of(staff));
        when(leaveTypes.findById(annual.getId())).thenReturn(Optional.of(annual));
        when(users.findById(employee.getId())).thenReturn(Optional.of(employee));
        when(requests.findOverlapping(any(), any(), any(), any())).thenReturn(List.of());
        when(requests.saveAndFlush(any())).thenAnswer(invocation -> {
            StaffLeaveRequest value = invocation.getArgument(0);
            value.setId(UUID.randomUUID());
            return value;
        });
    }

    @Test void activeEmployeeCreatesPrincipalOwnedPendingRequestAndAudit() {
        StaffLeaveRequestResponse result = service.create(request(
                LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 16)));
        assertThat(result.staff().staffProfileId()).isEqualTo(staff.getId());
        assertThat(result.status()).isEqualTo(LeaveRequestStatus.PENDING);
        assertThat(result.calendarDays()).isEqualTo(3);
        verify(profiles).findByIdForUpdate(staff.getId());
        verify(audit).log(eq("CREATE_LEAVE_REQUEST"), eq("HR"), eq(result.requestId()),
                eq(employee.getId()), contains("ANNUAL"));
    }

    @Test void sameDayLeaveHasOneCalendarDay() {
        LocalDate date = LocalDate.of(2026, 9, 14);
        assertThat(service.create(request(date, date)).calendarDays()).isEqualTo(1);
    }

    @Test void userWithoutStaffProfileIsRejected() {
        when(profiles.findByUserId(employee.getId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(request(LocalDate.now(), LocalDate.now())))
                .isInstanceOf(ResourceNotFoundException.class).hasMessageContaining("Staff Profile");
        verify(requests, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @EnumSource(value = EmploymentStatus.class, names = {"SUSPENDED", "INACTIVE", "TERMINATED"})
    void ineligibleEmploymentStatusesCannotSubmit(EmploymentStatus status) {
        staff.setEmploymentStatus(status);
        assertThatThrownBy(() -> service.create(request(LocalDate.now(), LocalDate.now())))
                .hasMessageContaining("ACTIVE");
        verify(requests, never()).saveAndFlush(any());
    }

    @Test void inactiveLeaveTypeCannotBeUsed() {
        annual.setActive(false);
        assertThatThrownBy(() -> service.create(request(LocalDate.now(), LocalDate.now())))
                .hasMessageContaining("ACTIVE");
    }

    @ParameterizedTest
    @EnumSource(value = LeaveRequestStatus.class, names = {"PENDING", "APPROVED"})
    void blockingOverlapIsRejected(LeaveRequestStatus status) {
        when(requests.findOverlapping(eq(staff.getId()), any(), any(), any()))
                .thenReturn(List.of(existing(status)));
        assertThatThrownBy(() -> service.create(request(
                LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 16))))
                .isInstanceOf(ResourceConflictException.class).hasMessageContaining("overlaps");
    }

    @ParameterizedTest
    @EnumSource(value = LeaveRequestStatus.class, names = {"REJECTED", "CANCELLED"})
    void nonBlockingStatusesDoNotParticipateInOverlapQuery(LeaveRequestStatus ignored) {
        service.create(request(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 16)));
        verify(requests).findOverlapping(eq(staff.getId()),
                argThat(values -> values.contains(LeaveRequestStatus.PENDING)
                        && values.contains(LeaveRequestStatus.APPROVED)
                        && !values.contains(ignored)), any(), any());
    }

    @Test void adjacentRangesAreAcceptedByInclusiveOverlapContract() {
        LocalDate start = LocalDate.of(2026, 9, 17);
        service.create(request(start, start.plusDays(1)));
        verify(requests).findOverlapping(staff.getId(),
                List.of(LeaveRequestStatus.PENDING, LeaveRequestStatus.APPROVED),
                start, start.plusDays(1));
    }

    @Test void invalidDateOrderIsRejectedBeforeLockOrPersistence() {
        assertThatThrownBy(() -> service.create(request(
                LocalDate.of(2026, 9, 16), LocalDate.of(2026, 9, 14))))
                .hasMessageContaining("before start");
        verify(profiles, never()).findByIdForUpdate(any());
    }

    @Test void selfHistoryIsRestrictedToPrincipalStaffProfile() {
        service.mine(LeaveRequestStatus.PENDING, null, null);
        verify(requests).search(staff.getId(), null, null, LeaveRequestStatus.PENDING, null, null);
    }

    @Test void managementReadUsesFiltersAndRequiresManagementRole() {
        when(roles.getCurrentRole()).thenReturn(Optional.of(Role.DIRECTOR));
        service.list(null, null, staff.getId(), staff.getDepartmentId(), annual.getId(), LeaveRequestStatus.PENDING);
        verify(requests).search(staff.getId(), staff.getDepartmentId(), annual.getId(),
                LeaveRequestStatus.PENDING, null, null);

        StaffLeaveRequest persisted = existing(LeaveRequestStatus.PENDING);
        persisted.setStaffProfileId(staff.getId());
        persisted.setLeaveTypeId(annual.getId());
        persisted.setStartDate(LocalDate.of(2026, 9, 14));
        persisted.setEndDate(LocalDate.of(2026, 9, 14));
        persisted.setReason("Family leave");
        when(requests.findById(persisted.getId())).thenReturn(Optional.of(persisted));
        assertThat(service.get(persisted.getId()).requestId()).isEqualTo(persisted.getId());

        when(roles.getCurrentRole()).thenReturn(Optional.of(Role.CASHIER));
        assertThatThrownBy(() -> service.get(UUID.randomUUID()))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    private CreateStaffLeaveRequest request(LocalDate start, LocalDate end) { return new CreateStaffLeaveRequest(annual.getId(), start, end, " Family leave "); }
    private StaffLeaveRequest existing(LeaveRequestStatus status) { StaffLeaveRequest v = new StaffLeaveRequest(); v.setId(UUID.randomUUID()); v.setStatus(status); return v; }
    private StaffProfile staff(EmploymentStatus status) { StaffProfile v = new StaffProfile(); v.setId(UUID.randomUUID()); v.setUserId(employee.getId()); v.setEmployeeCode("EMP-001"); v.setDepartmentId(UUID.randomUUID()); v.setEmploymentStatus(status); return v; }
    private LeaveType leaveType(boolean active) { LeaveType v = new LeaveType(); v.setId(UUID.randomUUID()); v.setCode("ANNUAL"); v.setName("Annual"); v.setActive(active); return v; }
    private User user(String username, String role) { User v = new User(); v.setId(UUID.randomUUID()); v.setUsername(username); v.setFullName(username); v.setRole(role); return v; }
}
