package com.casino.casinoerp;

import com.casino.casinoerp.entity.PitTableCustomerAssignment;
import com.casino.casinoerp.entity.PitTableCustomerAssignmentStatus;
import com.casino.casinoerp.entity.PitTableStaffAssignmentRole;
import com.casino.casinoerp.entity.User;
import com.casino.casinoerp.repository.PitTableCustomerAssignmentRepository;
import com.casino.casinoerp.repository.PitTableStaffAssignmentRepository;
import com.casino.casinoerp.security.Role;
import com.casino.casinoerp.service.AuthenticatedUserService;
import com.casino.casinoerp.service.CurrentUserRoleService;
import com.casino.casinoerp.service.PitTableAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class PitTableAccessServiceTests {
    private final AuthenticatedUserService authenticatedUsers = mock(AuthenticatedUserService.class);
    private final CurrentUserRoleService currentRoles = mock(CurrentUserRoleService.class);
    private final PitTableStaffAssignmentRepository staffAssignments = mock(PitTableStaffAssignmentRepository.class);
    private final PitTableCustomerAssignmentRepository customerAssignments =
            mock(PitTableCustomerAssignmentRepository.class);
    private final PitTableAccessService service = new PitTableAccessService(
            authenticatedUsers, currentRoles, staffAssignments, customerAssignments);

    @Test
    void dealerCanAccessOnlyExactActivelyAssignedOperation() {
        UUID userId = UUID.randomUUID();
        UUID assignedOperation = UUID.randomUUID();
        UUID otherOperation = UUID.randomUUID();
        User user = new User(); user.setId(userId);
        when(currentRoles.getCurrentRole()).thenReturn(Optional.of(Role.DEALER));
        when(authenticatedUsers.getRequiredUser()).thenReturn(user);
        when(staffAssignments.existsByPitTableIdAndStaffUserIdAndAssignmentRoleAndEndedAtIsNull(
                assignedOperation, userId, PitTableStaffAssignmentRole.DEALER)).thenReturn(true);

        assertThat(service.hasOperationalAccess(assignedOperation)).isTrue();
        assertThat(service.hasOperationalAccess(otherOperation)).isFalse();
        assertThatThrownBy(() -> service.requireOperationalAccess(otherOperation))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void endedOrMissingDealerAssignmentIsDenied() {
        UUID userId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        User user = new User(); user.setId(userId);
        when(currentRoles.getCurrentRole()).thenReturn(Optional.of(Role.DEALER));
        when(authenticatedUsers.getRequiredUser()).thenReturn(user);

        assertThat(service.hasOperationalAccess(operationId)).isFalse();
    }

    @Test
    void dealerCustomerSessionAccessRequiresActiveAssignmentAtAssignedOperation() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        User user = new User(); user.setId(userId);
        PitTableCustomerAssignment player = new PitTableCustomerAssignment();
        player.setPitTableId(operationId);
        when(currentRoles.getCurrentRole()).thenReturn(Optional.of(Role.DEALER));
        when(authenticatedUsers.getRequiredUser()).thenReturn(user);
        when(customerAssignments.findByCustomerSessionIdAndStatus(
                sessionId, PitTableCustomerAssignmentStatus.ACTIVE)).thenReturn(Optional.of(player));
        when(staffAssignments.existsByPitTableIdAndStaffUserIdAndAssignmentRoleAndEndedAtIsNull(
                operationId, userId, PitTableStaffAssignmentRole.DEALER)).thenReturn(true);

        service.requireCustomerSessionAccess(sessionId);

        verify(staffAssignments).existsByPitTableIdAndStaffUserIdAndAssignmentRoleAndEndedAtIsNull(
                operationId, userId, PitTableStaffAssignmentRole.DEALER);
    }

    @Test
    void supervisorAndSuperAdminRetainOperationalAccess() {
        UUID operationId = UUID.randomUUID();
        when(currentRoles.getCurrentRole()).thenReturn(Optional.of(Role.PIT_SUPERVISOR), Optional.of(Role.SUPER_ADMIN));

        assertThat(service.hasOperationalAccess(operationId)).isTrue();
        assertThat(service.hasOperationalAccess(operationId)).isTrue();
        verifyNoInteractions(authenticatedUsers, staffAssignments);
    }
}
