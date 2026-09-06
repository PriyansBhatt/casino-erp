package com.casino.casinoerp.service;

import com.casino.casinoerp.entity.PitTableCustomerAssignmentStatus;
import com.casino.casinoerp.entity.PitTableStaffAssignmentRole;
import com.casino.casinoerp.repository.PitTableCustomerAssignmentRepository;
import com.casino.casinoerp.repository.PitTableStaffAssignmentRepository;
import com.casino.casinoerp.security.Role;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class PitTableAccessService {
    private final AuthenticatedUserService authenticatedUsers;
    private final CurrentUserRoleService currentRoles;
    private final PitTableStaffAssignmentRepository staffAssignments;
    private final PitTableCustomerAssignmentRepository customerAssignments;

    public PitTableAccessService(
            AuthenticatedUserService authenticatedUsers,
            CurrentUserRoleService currentRoles,
            PitTableStaffAssignmentRepository staffAssignments,
            PitTableCustomerAssignmentRepository customerAssignments) {
        this.authenticatedUsers = authenticatedUsers;
        this.currentRoles = currentRoles;
        this.staffAssignments = staffAssignments;
        this.customerAssignments = customerAssignments;
    }

    public boolean isDealer() {
        return currentRoles.getCurrentRole().orElse(null) == Role.DEALER;
    }

    public boolean hasOperationalAccess(UUID operationId) {
        Role role = currentRoles.getCurrentRole().orElse(null);
        if (role == Role.SUPER_ADMIN || role == Role.PIT_SUPERVISOR) {
            return true;
        }
        if (role != Role.DEALER || operationId == null) {
            return false;
        }
        UUID userId = authenticatedUsers.getRequiredUser().getId();
        return staffAssignments.existsByPitTableIdAndStaffUserIdAndAssignmentRoleAndEndedAtIsNull(
                operationId, userId, PitTableStaffAssignmentRole.DEALER);
    }

    public void requireOperationalAccess(UUID operationId) {
        if (!hasOperationalAccess(operationId)) {
            throw new AccessDeniedException("Active Dealer assignment to this Pit Table operation is required.");
        }
    }

    public void requireManagementAccess() {
        Role role = currentRoles.getCurrentRole().orElse(null);
        if (role != Role.SUPER_ADMIN && role != Role.PIT_SUPERVISOR) {
            throw new AccessDeniedException("Pit Table management access is required.");
        }
    }

    public Optional<UUID> currentDealerOperationId() {
        if (!isDealer()) {
            return Optional.empty();
        }
        UUID userId = authenticatedUsers.getRequiredUser().getId();
        return staffAssignments.findByStaffUserIdAndAssignmentRoleAndEndedAtIsNull(
                        userId, PitTableStaffAssignmentRole.DEALER)
                .map(assignment -> assignment.getPitTableId());
    }

    public void requireCustomerSessionAccess(UUID sessionId) {
        if (!isDealer()) {
            return;
        }
        var assignment = customerAssignments.findByCustomerSessionIdAndStatus(
                        sessionId, PitTableCustomerAssignmentStatus.ACTIVE)
                .orElseThrow(() -> new AccessDeniedException(
                        "Customer session is not active at the Dealer's assigned Pit Table."));
        requireOperationalAccess(assignment.getPitTableId());
    }
}
