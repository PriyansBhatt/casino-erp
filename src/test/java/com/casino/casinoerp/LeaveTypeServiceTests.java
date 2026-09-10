package com.casino.casinoerp;

import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.exception.ResourceConflictException;
import com.casino.casinoerp.repository.LeaveTypeRepository;
import com.casino.casinoerp.security.Role;
import com.casino.casinoerp.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LeaveTypeServiceTests {
    private final LeaveTypeRepository repository = mock(LeaveTypeRepository.class);
    private final AuthenticatedUserService authenticated = mock(AuthenticatedUserService.class);
    private final CurrentUserRoleService roles = mock(CurrentUserRoleService.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final User actor = user();
    private LeaveTypeService service;

    @BeforeEach void setUp() {
        service = new LeaveTypeService(repository, authenticated, roles, new RolePermissionService(), audit);
        when(roles.getCurrentRole()).thenReturn(Optional.of(Role.DIRECTOR));
        when(authenticated.getRequiredUser()).thenReturn(actor);
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> saved(invocation.getArgument(0)));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test void createCanonicalizesExplicitCodeAndAudits() {
        LeaveTypeResponse result = service.create(new CreateLeaveTypeRequest(
                " annual ", " Annual Leave ", " Standard leave ", true));
        assertThat(result.code()).isEqualTo("ANNUAL");
        assertThat(result.name()).isEqualTo("Annual Leave");
        verify(audit).log(eq("CREATE_LEAVE_TYPE"), eq("HR"), eq(result.id()),
                eq(actor.getId()), contains("ANNUAL"));
    }

    @Test void duplicateCodeConflictsWithoutAudit() {
        when(repository.existsByCodeIgnoreCase("ANNUAL")).thenReturn(true);
        assertThatThrownBy(() -> service.create(new CreateLeaveTypeRequest(
                "annual", "Annual", null, true))).isInstanceOf(ResourceConflictException.class);
        verifyNoInteractions(audit);
    }

    @Test void updateCannotChangeImmutableCode() {
        LeaveType value = leaveType("ANNUAL", true);
        when(repository.findById(value.getId())).thenReturn(Optional.of(value));
        LeaveTypeResponse result = service.update(value.getId(),
                new UpdateLeaveTypeRequest("Annual Updated", null, false));
        assertThat(result.code()).isEqualTo("ANNUAL");
        assertThat(result.active()).isFalse();
        verify(audit).log(eq("UPDATE_LEAVE_TYPE"), eq("HR"), eq(value.getId()),
                eq(actor.getId()), contains("ANNUAL"));
    }

    @Test void nonManagementRoleCannotManageTypes() {
        when(roles.getCurrentRole()).thenReturn(Optional.of(Role.CASHIER));
        assertThatThrownBy(() -> service.list())
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    private LeaveType saved(LeaveType value) { if (value.getId() == null) value.setId(UUID.randomUUID()); return value; }
    private LeaveType leaveType(String code, boolean active) { LeaveType v = new LeaveType(); v.setId(UUID.randomUUID()); v.setCode(code); v.setName(code); v.setActive(active); return v; }
    private User user() { User v = new User(); v.setId(UUID.randomUUID()); v.setUsername("director"); v.setFullName("Director"); v.setRole("DIRECTOR"); return v; }
}
