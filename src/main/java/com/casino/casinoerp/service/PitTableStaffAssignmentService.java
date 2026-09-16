package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.exception.ResourceConflictException;
import com.casino.casinoerp.exception.ResourceNotFoundException;
import com.casino.casinoerp.repository.PitTableRepository;
import com.casino.casinoerp.repository.PitTableStaffAssignmentRepository;
import com.casino.casinoerp.repository.UserRepository;
import com.casino.casinoerp.security.Role;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class PitTableStaffAssignmentService {
    private final PitTableStaffAssignmentRepository assignments;
    private final PitTableRepository tables;
    private final UserRepository users;
    private final BusinessDateService businessDates;
    private final SystemLockService systemLock;
    private final CurrentUserRoleService currentRole;
    private final RolePermissionService permissions;
    private final AuthenticatedUserService authenticatedUser;
    private final AuditLogService audit;
    private final PitTableAccessService tableAccess;

    public PitTableStaffAssignmentService(
            PitTableStaffAssignmentRepository assignments, PitTableRepository tables,
            UserRepository users, BusinessDateService businessDates, SystemLockService systemLock,
            CurrentUserRoleService currentRole, RolePermissionService permissions,
            AuthenticatedUserService authenticatedUser, AuditLogService audit,
            PitTableAccessService tableAccess) {
        this.assignments = assignments;
        this.tables = tables;
        this.users = users;
        this.businessDates = businessDates;
        this.systemLock = systemLock;
        this.currentRole = currentRole;
        this.permissions = permissions;
        this.authenticatedUser = authenticatedUser;
        this.audit = audit;
        this.tableAccess = tableAccess;
    }

    @Transactional(readOnly = true)
    public List<PitTableStaffAssignmentResponse> getActive(UUID tableId) {
        requireViewPermission();
        requireTable(tableId);
        tableAccess.requireOperationalAccess(tableId);
        return assignments.findByPitTableIdAndEndedAtIsNullOrderByAssignmentRoleAsc(tableId)
                .stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public List<PitTableStaffAssignmentResponse> getHistory(UUID tableId) {
        requireViewPermission();
        requireTable(tableId);
        tableAccess.requireOperationalAccess(tableId);
        return assignments.findByPitTableIdOrderByStartedAtAsc(tableId)
                .stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public List<PitStaffCandidateResponse> getCandidates(PitTableStaffAssignmentRole role) {
        requireManagePermission();
        Role requiredRole = requiredUserRole(role);
        return users.findByStatusIgnoreCaseOrderByUsernameAsc("ACTIVE").stream()
                .filter(user -> Role.fromValue(user.getRole()).orElse(null) == requiredRole)
                .map(user -> new PitStaffCandidateResponse(user.getId(), user.getUsername(),
                        user.getFullName(), role, user.getStatus()))
                .toList();
    }

    @Transactional
    public PitTableStaffAssignmentResponse assign(UUID tableId, AssignPitTableStaffRequest request) {
        requireManagePermission();
        String key = request.idempotencyKey().trim();
        String remarks = normalizeRemarks(request.remarks());
        PitTableStaffAssignment replay = assignments.findByAssignmentIdempotencyKey(key).orElse(null);
        if (replay != null) {
            validateAssignReplay(replay, tableId, request.staffUserId(), request.assignmentRole(), remarks);
            return response(replay);
        }
        rejectKeyUsedForEnd(key);
        businessDates.validateNewOperationalMutationAllowed();

        PitTable table = mutationTable(tableId);
        User staff = requireEligibleStaff(request.staffUserId(), request.assignmentRole());
        validateNoActiveConflict(tableId, staff.getId(), request.assignmentRole());
        User actor = authenticatedUser.getRequiredUser();
        PitTableStaffAssignment saved = persistNew(table, staff, request.assignmentRole(), remarks, key, actor.getId());
        audit.log("PIT_STAFF_ASSIGNED", "PIT_TABLE_STAFF_ASSIGNMENT", saved.getId(), actor.getId(),
                auditContext(table, saved));
        return response(saved);
    }

    @Transactional
    public PitTableStaffAssignmentResponse end(
            UUID tableId, UUID assignmentId, EndPitTableStaffAssignmentRequest request) {
        requireManagePermission();
        String key = request.idempotencyKey().trim();
        String remarks = normalizeRemarks(request.remarks());
        PitTableStaffAssignment replay = assignments.findByEndIdempotencyKey(key).orElse(null);
        if (replay != null) {
            validateEndReplay(replay, tableId, assignmentId, remarks);
            return response(replay);
        }
        rejectKeyUsedForAssignment(key);
        businessDates.validateSettlementMutationAllowed();

        PitTable table = mutationTable(tableId);
        PitTableStaffAssignment assignment = assignments.findByIdForUpdate(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Pit Table staff assignment not found."));
        if (!tableId.equals(assignment.getPitTableId())) {
            throw new IllegalArgumentException("Staff assignment does not belong to the supplied Pit Table operation.");
        }
        if (!assignment.isActive()) {
            throw new ResourceConflictException("Pit Table staff assignment has already ended.");
        }
        User actor = authenticatedUser.getRequiredUser();
        endAssignment(assignment, actor.getId(), remarks, key);
        PitTableStaffAssignment saved = assignments.save(assignment);
        audit.log("PIT_STAFF_ASSIGNMENT_ENDED", "PIT_TABLE_STAFF_ASSIGNMENT", saved.getId(), actor.getId(),
                auditContext(table, saved));
        return response(saved);
    }

    @Transactional
    public PitTableStaffAssignmentResponse handover(
            UUID tableId, PitTableStaffAssignmentRole role, HandoverPitTableStaffRequest request) {
        requireManagePermission();
        String key = request.idempotencyKey().trim();
        String remarks = normalizeRemarks(request.remarks());
        PitTableStaffAssignment replay = assignments.findByAssignmentIdempotencyKey(key).orElse(null);
        if (replay != null) {
            validateAssignReplay(replay, tableId, request.newStaffUserId(), role, remarks);
            PitTableStaffAssignment previous = assignments.findByEndIdempotencyKey(key)
                    .orElseThrow(() -> new ResourceConflictException("Incomplete handover idempotency state detected."));
            if (!tableId.equals(previous.getPitTableId()) || previous.getAssignmentRole() != role
                    || !Objects.equals(remarks, previous.getEndRemarks())) {
                throw new ResourceConflictException("Idempotency key has already been used for a different handover.");
            }
            return response(replay);
        }
        if (assignments.findByEndIdempotencyKey(key).isPresent()) {
            throw new ResourceConflictException("Idempotency key has already been used for a different staff operation.");
        }
        businessDates.validateNewOperationalMutationAllowed();

        PitTable table = mutationTable(tableId);
        PitTableStaffAssignment previous = assignments.findByPitTableIdAndAssignmentRoleAndEndedAtIsNull(tableId, role)
                .orElseThrow(() -> new ResourceConflictException("No active " + role + " assignment exists to hand over."));
        User replacement = requireEligibleStaff(request.newStaffUserId(), role);
        if (replacement.getId().equals(previous.getStaffUserId())) {
            throw new ResourceConflictException("Replacement staff user is already assigned in this role.");
        }
        validateNoUserConflict(replacement.getId(), role);
        User actor = authenticatedUser.getRequiredUser();
        endAssignment(previous, actor.getId(), remarks, key);
        assignments.saveAndFlush(previous);
        PitTableStaffAssignment next = persistNew(table, replacement, role, remarks, key, actor.getId());
        audit.log("PIT_STAFF_HANDOVER", "PIT_TABLE_STAFF_ASSIGNMENT", next.getId(), actor.getId(),
                auditContext(table, next) + ", previousAssignmentId=" + previous.getId());
        return response(next);
    }

    private PitTable mutationTable(UUID tableId) {
        businessDates.validateBusinessDateIsOpen();
        if (systemLock.isSystemLocked()) {
            throw new ResourceConflictException("System is locked. Pit Table staff assignments are not allowed.");
        }
        PitTable table = tables.findByIdForUpdate(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Pit Table operation not found."));
        assignments.findActiveByPitTableIdForUpdate(tableId);
        if (!"OPEN".equalsIgnoreCase(table.getStatus())) {
            throw new ResourceConflictException("Pit Table operation must be OPEN for staff assignment changes.");
        }
        LocalDate currentBusinessDate = businessDates.getCurrentBusinessDate();
        if (!currentBusinessDate.equals(table.getBusinessDate())) {
            throw new ResourceConflictException("Pit Table operation does not belong to the current OPEN Business Date.");
        }
        return table;
    }

    private User requireEligibleStaff(UUID userId, PitTableStaffAssignmentRole assignmentRole) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Pit staff user not found."));
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new ResourceConflictException("Pit staff user must be ACTIVE.");
        }
        Role actualRole = canonicalRole(user);
        Role requiredRole = requiredUserRole(assignmentRole);
        if (actualRole != requiredRole) {
            throw new IllegalArgumentException("Selected user must have canonical role " + requiredRole + ".");
        }
        return user;
    }

    private Role canonicalRole(User user) {
        return Role.fromValue(user.getRole())
                .orElseThrow(() -> new IllegalArgumentException("Selected user has an unknown role."));
    }

    private Role requiredUserRole(PitTableStaffAssignmentRole role) {
        return role == PitTableStaffAssignmentRole.DEALER ? Role.DEALER : Role.PIT_SUPERVISOR;
    }

    private void validateNoActiveConflict(UUID tableId, UUID staffUserId, PitTableStaffAssignmentRole role) {
        if (assignments.findByPitTableIdAndAssignmentRoleAndEndedAtIsNull(tableId, role).isPresent()) {
            throw new ResourceConflictException("This Pit Table operation already has an active " + role + ".");
        }
        validateNoUserConflict(staffUserId, role);
    }

    private void validateNoUserConflict(UUID staffUserId, PitTableStaffAssignmentRole role) {
        if (role == PitTableStaffAssignmentRole.DEALER
                && assignments.findByStaffUserIdAndAssignmentRoleAndEndedAtIsNull(staffUserId, role).isPresent()) {
            throw new ResourceConflictException("Dealer already has an active Pit Table assignment.");
        }
    }

    private PitTableStaffAssignment persistNew(PitTable table, User staff, PitTableStaffAssignmentRole role,
            String remarks, String key, UUID actorId) {
        LocalDateTime now = LocalDateTime.now();
        PitTableStaffAssignment assignment = new PitTableStaffAssignment();
        assignment.setPitTableId(table.getId());
        assignment.setStaffUserId(staff.getId());
        assignment.setAssignmentRole(role);
        assignment.setBusinessDate(table.getBusinessDate());
        assignment.setStartedAt(now);
        assignment.setAssignedBy(actorId);
        assignment.setRemarks(remarks);
        assignment.setAssignmentIdempotencyKey(key);
        assignment.setCreatedAt(now);
        try {
            return assignments.saveAndFlush(assignment);
        } catch (DataIntegrityViolationException exception) {
            throw new ResourceConflictException(
                    "Pit Table staff assignment conflicts with an active assignment or idempotency key.");
        }
    }

    private void endAssignment(PitTableStaffAssignment assignment, UUID actorId, String remarks, String key) {
        assignment.setEndedAt(LocalDateTime.now());
        assignment.setEndedBy(actorId);
        assignment.setEndRemarks(remarks);
        assignment.setEndIdempotencyKey(key);
    }

    private PitTableStaffAssignmentResponse response(PitTableStaffAssignment assignment) {
        User staff = users.findById(assignment.getStaffUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Assigned staff user not found."));
        return new PitTableStaffAssignmentResponse(
                assignment.getId(), assignment.getPitTableId(), staff.getId(), staff.getUsername(),
                staff.getFullName(), assignment.getAssignmentRole(), assignment.getBusinessDate(),
                assignment.getStartedAt(), assignment.getEndedAt(), actor(assignment.getAssignedBy()),
                actor(assignment.getEndedBy()), assignment.getRemarks(), assignment.getEndRemarks(),
                assignment.isActive());
    }

    private ActorReferenceResponse actor(UUID userId) {
        if (userId == null) return null;
        User user = users.findById(userId).orElse(null);
        return new ActorReferenceResponse(userId, user == null ? null : user.getUsername());
    }

    private PitTable requireTable(UUID tableId) {
        return tables.findById(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Pit Table operation not found."));
    }

    private void requireManagePermission() {
        if (!currentRole.getCurrentRole().map(permissions::canManagePitTableStaff).orElse(false)) {
            throw new RuntimeException("Access denied. Only Pit Supervisor or Super Admin can manage Pit Table staff.");
        }
    }

    private void requireViewPermission() {
        if (!currentRole.getCurrentRole().map(permissions::canViewPitTableStaff).orElse(false)) {
            throw new RuntimeException("Access denied. Only Dealer, Pit Supervisor or Super Admin can view Pit Table staff.");
        }
    }

    private void validateAssignReplay(PitTableStaffAssignment replay, UUID tableId, UUID staffUserId,
            PitTableStaffAssignmentRole role, String remarks) {
        if (!Objects.equals(replay.getAssignedBy(), authenticatedUser.getRequiredUser().getId())
                || !tableId.equals(replay.getPitTableId()) || !staffUserId.equals(replay.getStaffUserId())
                || role != replay.getAssignmentRole() || !Objects.equals(remarks, replay.getRemarks())) {
            throw new ResourceConflictException("Idempotency key has already been used for a different staff assignment.");
        }
    }

    private void validateEndReplay(PitTableStaffAssignment replay, UUID tableId, UUID assignmentId, String remarks) {
        if (!Objects.equals(replay.getEndedBy(), authenticatedUser.getRequiredUser().getId())
                || !assignmentId.equals(replay.getId()) || !tableId.equals(replay.getPitTableId())
                || !Objects.equals(remarks, replay.getEndRemarks())) {
            throw new ResourceConflictException("Idempotency key has already been used for a different assignment end.");
        }
    }

    private void rejectKeyUsedForAssignment(String key) {
        if (assignments.findByAssignmentIdempotencyKey(key).isPresent()) {
            throw new ResourceConflictException("Idempotency key has already been used for a different staff operation.");
        }
    }

    private void rejectKeyUsedForEnd(String key) {
        if (assignments.findByEndIdempotencyKey(key).isPresent()) {
            throw new ResourceConflictException("Idempotency key has already been used for a different staff operation.");
        }
    }

    private String normalizeRemarks(String remarks) {
        return remarks == null || remarks.trim().isEmpty() ? null : remarks.trim();
    }

    private String auditContext(PitTable table, PitTableStaffAssignment assignment) {
        return "tableOperationId=" + table.getId() + ", physicalTableId=" + table.getPhysicalTableId()
                + ", tableCode=" + table.getTableCode() + ", businessDate=" + table.getBusinessDate()
                + ", assignmentId=" + assignment.getId() + ", assignmentRole=" + assignment.getAssignmentRole()
                + ", staffUserId=" + assignment.getStaffUserId();
    }
}
