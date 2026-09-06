package com.casino.casinoerp;

import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.exception.ResourceConflictException;
import com.casino.casinoerp.exception.ResourceNotFoundException;
import com.casino.casinoerp.repository.PitTableRepository;
import com.casino.casinoerp.repository.PitTableStaffAssignmentRepository;
import com.casino.casinoerp.repository.UserRepository;
import com.casino.casinoerp.security.Role;
import com.casino.casinoerp.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PitTableStaffAssignmentServiceTests {
    private static final UUID TABLE_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PHYSICAL_ID = UUID.fromString("11000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID DEALER_A_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID DEALER_B_ID = UUID.fromString("30000000-0000-0000-0000-000000000002");
    private static final UUID SUPERVISOR_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final LocalDate DATE = LocalDate.of(2026, 9, 2);

    @Mock PitTableStaffAssignmentRepository assignments;
    @Mock PitTableRepository tables;
    @Mock UserRepository users;
    @Mock BusinessDateService businessDates;
    @Mock SystemLockService systemLock;
    @Mock CurrentUserRoleService currentRole;
    @Mock RolePermissionService permissions;
    @Mock AuthenticatedUserService authenticatedUser;
    @Mock AuditLogService audit;
    PitTableStaffAssignmentService service;

    @BeforeEach
    void setUp() {
        service = new PitTableStaffAssignmentService(assignments, tables, users, businessDates,
                systemLock, currentRole, permissions, authenticatedUser, audit);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"SUPER_ADMIN", "PIT_SUPERVISOR"})
    void authorizedManagerAssignsDealerAndAuditsOnce(Role actorRole) {
        allow(actorRole);
        prepareMutation(openTable(DATE));
        User dealer = user(DEALER_A_ID, "dealer-a", Role.DEALER, "ACTIVE");
        when(users.findById(DEALER_A_ID)).thenReturn(Optional.of(dealer));
        when(authenticatedUser.getRequiredUser()).thenReturn(actor(actorRole));
        saveAnswers();

        var response = service.assign(TABLE_ID, assignRequest(DEALER_A_ID, PitTableStaffAssignmentRole.DEALER, "assign-1"));

        assertThat(response.staffUserId()).isEqualTo(DEALER_A_ID);
        assertThat(response.assignmentRole()).isEqualTo(PitTableStaffAssignmentRole.DEALER);
        verify(audit, times(1)).log(eq("PIT_STAFF_ASSIGNED"), eq("PIT_TABLE_STAFF_ASSIGNMENT"),
                any(), eq(ACTOR_ID), any());
    }

    @Test
    void dealerCannotAssignStaff() {
        when(currentRole.getCurrentRole()).thenReturn(Optional.of(Role.DEALER));
        when(permissions.canManagePitTableStaff(Role.DEALER)).thenReturn(false);
        assertThatThrownBy(() -> service.assign(TABLE_ID,
                assignRequest(DEALER_A_ID, PitTableStaffAssignmentRole.DEALER, "assign-denied")))
                .hasMessageContaining("Only Pit Supervisor or Super Admin");
        verifyNoInteractions(tables);
    }

    @Test
    void unknownInactiveAndWrongRoleUsersAreRejected() {
        allow(Role.SUPER_ADMIN);
        prepareMutation(openTable(DATE));
        when(users.findById(DEALER_A_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.assign(TABLE_ID,
                assignRequest(DEALER_A_ID, PitTableStaffAssignmentRole.DEALER, "unknown")))
                .isInstanceOf(ResourceNotFoundException.class);

        when(users.findById(DEALER_A_ID)).thenReturn(Optional.of(user(DEALER_A_ID, "inactive", Role.DEALER, "INACTIVE")));
        assertThatThrownBy(() -> service.assign(TABLE_ID,
                assignRequest(DEALER_A_ID, PitTableStaffAssignmentRole.DEALER, "inactive")))
                .hasMessageContaining("must be ACTIVE");

        when(users.findById(DEALER_A_ID)).thenReturn(Optional.of(user(DEALER_A_ID, "cashier", Role.CASHIER, "ACTIVE")));
        assertThatThrownBy(() -> service.assign(TABLE_ID,
                assignRequest(DEALER_A_ID, PitTableStaffAssignmentRole.DEALER, "wrong-role")))
                .hasMessageContaining("canonical role DEALER");
    }

    @Test
    void supervisorAssignmentRequiresSupervisorUser() {
        allow(Role.PIT_SUPERVISOR);
        prepareMutation(openTable(DATE));
        User supervisor = user(SUPERVISOR_ID, "pit", Role.PIT_SUPERVISOR, "ACTIVE");
        when(users.findById(SUPERVISOR_ID)).thenReturn(Optional.of(supervisor));
        when(authenticatedUser.getRequiredUser()).thenReturn(actor(Role.PIT_SUPERVISOR));
        saveAnswers();
        var response = service.assign(TABLE_ID,
                assignRequest(SUPERVISOR_ID, PitTableStaffAssignmentRole.PIT_SUPERVISOR, "supervisor-1"));
        assertThat(response.assignmentRole()).isEqualTo(PitTableStaffAssignmentRole.PIT_SUPERVISOR);
    }

    @Test
    void closedNonCurrentAndLockedOperationsAreRejected() {
        allow(Role.SUPER_ADMIN);
        when(systemLock.isSystemLocked()).thenReturn(true);
        assertThatThrownBy(() -> service.assign(TABLE_ID,
                assignRequest(DEALER_A_ID, PitTableStaffAssignmentRole.DEALER, "locked")))
                .hasMessageContaining("System is locked");

        reset(systemLock, tables, businessDates, assignments);
        when(businessDates.getCurrentBusinessDate()).thenReturn(DATE);
        when(tables.findByIdForUpdate(TABLE_ID)).thenReturn(Optional.of(openTable(DATE).withStatus("CLOSED")));
        when(assignments.findActiveByPitTableIdForUpdate(TABLE_ID)).thenReturn(List.of());
        assertThatThrownBy(() -> service.assign(TABLE_ID,
                assignRequest(DEALER_A_ID, PitTableStaffAssignmentRole.DEALER, "closed")))
                .hasMessageContaining("must be OPEN");

        when(tables.findByIdForUpdate(TABLE_ID)).thenReturn(Optional.of(openTable(DATE.minusDays(1))));
        assertThatThrownBy(() -> service.assign(TABLE_ID,
                assignRequest(DEALER_A_ID, PitTableStaffAssignmentRole.DEALER, "old")))
                .hasMessageContaining("current OPEN Business Date");
    }

    @Test
    void activeRoleAndDealerConflictsAreRejected() {
        allow(Role.SUPER_ADMIN);
        prepareMutation(openTable(DATE));
        User dealer = user(DEALER_A_ID, "dealer-a", Role.DEALER, "ACTIVE");
        when(users.findById(DEALER_A_ID)).thenReturn(Optional.of(dealer));
        when(assignments.findByPitTableIdAndAssignmentRoleAndEndedAtIsNull(
                TABLE_ID, PitTableStaffAssignmentRole.DEALER)).thenReturn(Optional.of(assignment(DEALER_B_ID)));
        assertThatThrownBy(() -> service.assign(TABLE_ID,
                assignRequest(DEALER_A_ID, PitTableStaffAssignmentRole.DEALER, "table-conflict")))
                .hasMessageContaining("already has an active DEALER");

        when(assignments.findByPitTableIdAndAssignmentRoleAndEndedAtIsNull(
                TABLE_ID, PitTableStaffAssignmentRole.DEALER)).thenReturn(Optional.empty());
        when(assignments.findByStaffUserIdAndAssignmentRoleAndEndedAtIsNull(
                DEALER_A_ID, PitTableStaffAssignmentRole.DEALER)).thenReturn(Optional.of(assignment(DEALER_A_ID)));
        assertThatThrownBy(() -> service.assign(TABLE_ID,
                assignRequest(DEALER_A_ID, PitTableStaffAssignmentRole.DEALER, "user-conflict")))
                .hasMessageContaining("Dealer already has an active");
    }

    @Test
    void exactAssignmentRetryReturnsOriginalWithoutAudit() {
        allow(Role.SUPER_ADMIN);
        PitTableStaffAssignment original = assignment(DEALER_A_ID);
        original.setRemarks("Shift");
        original.setAssignmentIdempotencyKey("same");
        when(assignments.findByAssignmentIdempotencyKey("same")).thenReturn(Optional.of(original));
        stubResponseUsers(original, user(DEALER_A_ID, "dealer-a", Role.DEALER, "ACTIVE"));

        var response = service.assign(TABLE_ID,
                new AssignPitTableStaffRequest(DEALER_A_ID, PitTableStaffAssignmentRole.DEALER, " Shift ", "same"));
        assertThat(response.assignmentId()).isEqualTo(original.getId());
        verify(assignments, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    @Test
    void conflictingAssignmentRetryIsRejected() {
        allow(Role.SUPER_ADMIN);
        PitTableStaffAssignment original = assignment(DEALER_A_ID);
        original.setAssignmentIdempotencyKey("same");
        when(assignments.findByAssignmentIdempotencyKey("same")).thenReturn(Optional.of(original));
        assertThatThrownBy(() -> service.assign(TABLE_ID,
                assignRequest(DEALER_B_ID, PitTableStaffAssignmentRole.DEALER, "same")))
                .hasMessageContaining("different staff assignment");
    }

    @Test
    void endingAssignmentPreservesHistoryAndIsIdempotent() {
        allow(Role.PIT_SUPERVISOR);
        prepareMutation(openTable(DATE));
        PitTableStaffAssignment current = assignment(DEALER_A_ID);
        when(assignments.findByIdForUpdate(current.getId())).thenReturn(Optional.of(current));
        when(authenticatedUser.getRequiredUser()).thenReturn(actor(Role.PIT_SUPERVISOR));
        when(assignments.save(current)).thenReturn(current);
        stubResponseUsers(current, user(DEALER_A_ID, "dealer-a", Role.DEALER, "ACTIVE"));

        var ended = service.end(TABLE_ID, current.getId(), new EndPitTableStaffAssignmentRequest("End", "end-1"));
        assertThat(ended.active()).isFalse();
        assertThat(current.getEndedAt()).isNotNull();
        assertThat(current.getEndIdempotencyKey()).isEqualTo("end-1");
        verify(assignments, never()).delete(any());

        when(assignments.findByEndIdempotencyKey("end-1")).thenReturn(Optional.of(current));
        service.end(TABLE_ID, current.getId(), new EndPitTableStaffAssignmentRequest("End", "end-1"));
        verify(audit, times(1)).log(eq("PIT_STAFF_ASSIGNMENT_ENDED"), any(), any(), any(), any());
    }

    @Test
    void dealerHandoverEndsPreviousAndCreatesReplacementAtomically() {
        allow(Role.PIT_SUPERVISOR);
        prepareMutation(openTable(DATE));
        PitTableStaffAssignment previous = assignment(DEALER_A_ID);
        User replacement = user(DEALER_B_ID, "dealer-b", Role.DEALER, "ACTIVE");
        when(assignments.findByPitTableIdAndAssignmentRoleAndEndedAtIsNull(
                TABLE_ID, PitTableStaffAssignmentRole.DEALER)).thenReturn(Optional.of(previous));
        when(users.findById(DEALER_B_ID)).thenReturn(Optional.of(replacement));
        when(authenticatedUser.getRequiredUser()).thenReturn(actor(Role.PIT_SUPERVISOR));
        saveAnswers();

        var next = service.handover(TABLE_ID, PitTableStaffAssignmentRole.DEALER,
                new HandoverPitTableStaffRequest(DEALER_B_ID, "Handover", "handover-1"));
        assertThat(previous.isActive()).isFalse();
        assertThat(previous.getEndIdempotencyKey()).isEqualTo("handover-1");
        assertThat(next.staffUserId()).isEqualTo(DEALER_B_ID);
        verify(audit, times(1)).log(eq("PIT_STAFF_HANDOVER"), any(), any(), eq(ACTOR_ID), any());
    }

    @Test
    void failedReplacementValidationLeavesPreviousActive() {
        allow(Role.PIT_SUPERVISOR);
        prepareMutation(openTable(DATE));
        PitTableStaffAssignment previous = assignment(DEALER_A_ID);
        when(assignments.findByPitTableIdAndAssignmentRoleAndEndedAtIsNull(
                TABLE_ID, PitTableStaffAssignmentRole.DEALER)).thenReturn(Optional.of(previous));
        when(users.findById(DEALER_B_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.handover(TABLE_ID, PitTableStaffAssignmentRole.DEALER,
                new HandoverPitTableStaffRequest(DEALER_B_ID, null, "handover-fail")))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(previous.isActive()).isTrue();
        verify(assignments, never()).save(any());
    }

    @Test
    void exactHandoverRetryReturnsReplacementWithoutWritingOrAuditing() {
        allow(Role.SUPER_ADMIN);
        PitTableStaffAssignment previous = assignment(DEALER_A_ID);
        previous.setEndedAt(java.time.LocalDateTime.now());
        previous.setEndedBy(ACTOR_ID);
        previous.setEndRemarks("Handover");
        previous.setEndIdempotencyKey("handover-retry");
        PitTableStaffAssignment replacement = assignment(DEALER_B_ID);
        replacement.setRemarks("Handover");
        replacement.setAssignmentIdempotencyKey("handover-retry");
        when(assignments.findByAssignmentIdempotencyKey("handover-retry")).thenReturn(Optional.of(replacement));
        when(assignments.findByEndIdempotencyKey("handover-retry")).thenReturn(Optional.of(previous));
        stubResponseUsers(replacement, user(DEALER_B_ID, "dealer-b", Role.DEALER, "ACTIVE"));

        var result = service.handover(TABLE_ID, PitTableStaffAssignmentRole.DEALER,
                new HandoverPitTableStaffRequest(DEALER_B_ID, "Handover", "handover-retry"));
        assertThat(result.assignmentId()).isEqualTo(replacement.getId());
        verify(assignments, never()).save(any());
        verify(assignments, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    @Test
    void conflictingEndAndHandoverRetriesAreRejected() {
        allow(Role.SUPER_ADMIN);
        PitTableStaffAssignment ended = assignment(DEALER_A_ID);
        ended.setEndedAt(java.time.LocalDateTime.now());
        ended.setEndedBy(ACTOR_ID);
        ended.setEndRemarks("Original");
        ended.setEndIdempotencyKey("end-conflict");
        when(assignments.findByEndIdempotencyKey("end-conflict")).thenReturn(Optional.of(ended));
        assertThatThrownBy(() -> service.end(TABLE_ID, ended.getId(),
                new EndPitTableStaffAssignmentRequest("Changed", "end-conflict")))
                .hasMessageContaining("different assignment end");

        PitTableStaffAssignment replacement = assignment(DEALER_B_ID);
        replacement.setRemarks("Original");
        replacement.setAssignmentIdempotencyKey("handover-conflict");
        when(assignments.findByAssignmentIdempotencyKey("handover-conflict"))
                .thenReturn(Optional.of(replacement));
        assertThatThrownBy(() -> service.handover(TABLE_ID, PitTableStaffAssignmentRole.DEALER,
                new HandoverPitTableStaffRequest(DEALER_A_ID, "Changed", "handover-conflict")))
                .hasMessageContaining("different staff assignment");
    }

    @Test
    void activeAndHistoryQueriesRemainSeparate() {
        when(currentRole.getCurrentRole()).thenReturn(Optional.of(Role.DEALER));
        when(permissions.canViewPitTableStaff(Role.DEALER)).thenReturn(true);
        PitTableStaffAssignment active = assignment(DEALER_A_ID);
        PitTableStaffAssignment ended = assignment(DEALER_B_ID);
        ended.setEndedAt(java.time.LocalDateTime.now()); ended.setEndedBy(ACTOR_ID);
        when(tables.findById(TABLE_ID)).thenReturn(Optional.of(openTable(DATE)));
        when(assignments.findByPitTableIdAndEndedAtIsNullOrderByAssignmentRoleAsc(TABLE_ID))
                .thenReturn(List.of(active));
        when(assignments.findByPitTableIdOrderByStartedAtAsc(TABLE_ID)).thenReturn(List.of(ended, active));
        stubResponseUsers(active, user(DEALER_A_ID, "dealer-a", Role.DEALER, "ACTIVE"));
        when(users.findById(DEALER_B_ID)).thenReturn(Optional.of(user(DEALER_B_ID, "dealer-b", Role.DEALER, "ACTIVE")));

        assertThat(service.getActive(TABLE_ID)).hasSize(1);
        assertThat(service.getHistory(TABLE_ID)).hasSize(2);
    }

    @Test
    void candidateLookupReturnsOnlyActiveMatchingCanonicalRole() {
        allow(Role.SUPER_ADMIN);
        User legacyDealer = user(DEALER_A_ID, "dealer-a", Role.DEALER, "ACTIVE");
        legacyDealer.setRole(" Dealer ");
        User supervisor = user(SUPERVISOR_ID, "pit", Role.PIT_SUPERVISOR, "ACTIVE");
        when(users.findByStatusIgnoreCaseOrderByUsernameAsc("ACTIVE")).thenReturn(List.of(legacyDealer, supervisor));
        var result = service.getCandidates(PitTableStaffAssignmentRole.DEALER);
        assertThat(result).extracting(PitStaffCandidateResponse::id).containsExactly(DEALER_A_ID);
    }

    private void allow(Role role) {
        when(currentRole.getCurrentRole()).thenReturn(Optional.of(role));
        when(permissions.canManagePitTableStaff(role)).thenReturn(true);
    }

    private void prepareMutation(PitTable table) {
        when(assignments.findByAssignmentIdempotencyKey(any())).thenReturn(Optional.empty());
        when(assignments.findByEndIdempotencyKey(any())).thenReturn(Optional.empty());
        when(tables.findByIdForUpdate(TABLE_ID)).thenReturn(Optional.of(table));
        when(assignments.findActiveByPitTableIdForUpdate(TABLE_ID)).thenReturn(List.of());
        when(businessDates.getCurrentBusinessDate()).thenReturn(DATE);
    }

    private void saveAnswers() {
        when(assignments.saveAndFlush(any())).thenAnswer(invocation -> {
            PitTableStaffAssignment value = invocation.getArgument(0);
            if (value.getId() == null) value.setId(UUID.randomUUID());
            return value;
        });
        when(users.findById(ACTOR_ID)).thenReturn(Optional.of(actor(Role.SUPER_ADMIN)));
    }

    private void stubResponseUsers(PitTableStaffAssignment assignment, User staff) {
        when(users.findById(assignment.getStaffUserId())).thenReturn(Optional.of(staff));
        when(users.findById(assignment.getAssignedBy())).thenReturn(Optional.of(actor(Role.SUPER_ADMIN)));
    }

    private AssignPitTableStaffRequest assignRequest(UUID userId, PitTableStaffAssignmentRole role, String key) {
        return new AssignPitTableStaffRequest(userId, role, "Shift", key);
    }

    private TestPitTable openTable(LocalDate date) {
        TestPitTable table = new TestPitTable();
        table.setId(TABLE_ID); table.setPhysicalTableId(PHYSICAL_ID); table.setTableCode("BAC-001");
        table.setStatus("OPEN"); table.setBusinessDate(date);
        return table;
    }

    private PitTableStaffAssignment assignment(UUID staffId) {
        PitTableStaffAssignment value = new PitTableStaffAssignment();
        value.setId(UUID.randomUUID()); value.setPitTableId(TABLE_ID); value.setStaffUserId(staffId);
        value.setAssignmentRole(PitTableStaffAssignmentRole.DEALER); value.setBusinessDate(DATE);
        value.setStartedAt(java.time.LocalDateTime.now()); value.setCreatedAt(java.time.LocalDateTime.now());
        value.setAssignedBy(ACTOR_ID); value.setRemarks("Shift"); value.setAssignmentIdempotencyKey("original");
        return value;
    }

    private User actor(Role role) { return user(ACTOR_ID, "actor", role, "ACTIVE"); }

    private User user(UUID id, String username, Role role, String status) {
        User user = new User(); user.setId(id); user.setUsername(username); user.setFullName(username);
        user.setRole(role.name()); user.setStatus(status); return user;
    }

    private static class TestPitTable extends PitTable {
        TestPitTable withStatus(String status) { setStatus(status); return this; }
    }
}
