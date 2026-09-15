package com.casino.casinoerp;

import com.casino.casinoerp.dto.ChipCustodyTransferRequest;
import com.casino.casinoerp.dto.OpenPitTableOperationRequest;
import com.casino.casinoerp.entity.PhysicalPitTable;
import com.casino.casinoerp.entity.PitTable;
import com.casino.casinoerp.entity.User;
import com.casino.casinoerp.exception.ResourceConflictException;
import com.casino.casinoerp.repository.ChipCustodyMovementRepository;
import com.casino.casinoerp.repository.PhysicalPitTableRepository;
import com.casino.casinoerp.repository.PitTableCustomerAssignmentRepository;
import com.casino.casinoerp.repository.PitTableRepository;
import com.casino.casinoerp.repository.PitReadRepository;
import com.casino.casinoerp.repository.PitTableActiveStaffProjection;
import com.casino.casinoerp.repository.PitTableStaffAssignmentRepository;
import com.casino.casinoerp.repository.VerifiedGamingResultRepository;
import com.casino.casinoerp.security.Role;
import com.casino.casinoerp.service.AuditLogService;
import com.casino.casinoerp.service.AuthenticatedUserService;
import com.casino.casinoerp.service.BusinessDateService;
import com.casino.casinoerp.service.ChipCustodyService;
import com.casino.casinoerp.service.CurrentUserRoleService;
import com.casino.casinoerp.service.PitTableOperationService;
import com.casino.casinoerp.service.PitTableAccessService;
import com.casino.casinoerp.service.RolePermissionService;
import com.casino.casinoerp.service.SystemLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PitTableOperationServiceTests {
    private static final UUID PHYSICAL_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OPERATION_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID HISTORICAL_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID DEALER_ASSIGNMENT_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID DEALER_USER_ID = UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final UUID SUPERVISOR_ASSIGNMENT_ID = UUID.fromString("50000000-0000-0000-0000-000000000002");
    private static final UUID SUPERVISOR_USER_ID = UUID.fromString("60000000-0000-0000-0000-000000000002");
    private static final LocalDate CURRENT_DATE = LocalDate.of(2026, 9, 2);

    @Mock PhysicalPitTableRepository physicalTables;
    @Mock PitTableRepository operations;
    @Mock PitReadRepository reads;
    @Mock PitTableStaffAssignmentRepository staffAssignments;
    @Mock ChipCustodyService custody;
    @Mock BusinessDateService businessDates;
    @Mock SystemLockService systemLock;
    @Mock CurrentUserRoleService currentRole;
    @Mock RolePermissionService permissions;
    @Mock AuthenticatedUserService authenticatedUser;
    @Mock AuditLogService audit;
    @Mock PitTableAccessService tableAccess;

    private PitTableOperationService service;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(reads.summarize(any())).thenAnswer(call -> {
            List<UUID> ids = call.getArgument(0);
            Map<UUID, PitReadRepository.Totals> values = new java.util.HashMap<>();
            ids.forEach(id -> values.put(id, PitReadRepository.Totals.ZERO));
            return values;
        });
        service = new PitTableOperationService(physicalTables, operations, reads,
                staffAssignments, custody, businessDates, systemLock, currentRole,
                permissions, authenticatedUser, audit, tableAccess);
    }

    @Test
    void historicalClosedOperationDoesNotPreventLaterBusinessDateOpening() {
        allow(Role.SUPER_ADMIN);
        PhysicalPitTable physical = physical();
        PitTable historical = operation(HISTORICAL_ID, LocalDate.of(2026, 6, 17), "CLOSED");
        when(physicalTables.findByIdForUpdate(PHYSICAL_ID)).thenReturn(Optional.of(physical));
        when(operations.findByPhysicalTableIdAndBusinessDate(PHYSICAL_ID, CURRENT_DATE))
                .thenReturn(Optional.empty());
        when(operations.findFirstByPhysicalTableIdAndStatusIgnoreCase(PHYSICAL_ID, "OPEN"))
                .thenReturn(Optional.empty());
        when(custody.calculateDenominationTotal(Map.of(5000, 20L)))
                .thenReturn(new BigDecimal("100000"));
        when(operations.saveAndFlush(any())).thenAnswer(invocation -> {
            PitTable value = invocation.getArgument(0);
            value.setId(OPERATION_ID);
            return value;
        });

        var response = service.open(PHYSICAL_ID, request("open-1", "Daily opening"));

        assertEquals(OPERATION_ID, response.id());
        assertNotEquals(HISTORICAL_ID, response.id());
        assertEquals(CURRENT_DATE, response.businessDate());
        assertEquals(new BigDecimal("100000"), response.openingFloat());
        assertEquals("CLOSED", historical.getStatus());
        verify(businessDates).validateNewOperationalMutationAllowed();
        verify(custody).issueTableFloat(org.mockito.ArgumentMatchers.eq(OPERATION_ID), any());
        verify(audit).log(org.mockito.ArgumentMatchers.eq("OPEN_PIT_TABLE_OPERATION"),
                org.mockito.ArgumentMatchers.eq("PIT_TABLE"),
                org.mockito.ArgumentMatchers.eq(OPERATION_ID),
                org.mockito.ArgumentMatchers.eq(ACTOR_ID), any());
    }

    @Test
    void duplicateCurrentBusinessDateOperationIsRejectedBeforeCustody() {
        allow(Role.SUPER_ADMIN);
        when(physicalTables.findByIdForUpdate(PHYSICAL_ID)).thenReturn(Optional.of(physical()));
        when(operations.findByPhysicalTableIdAndBusinessDate(PHYSICAL_ID, CURRENT_DATE))
                .thenReturn(Optional.of(operation(OPERATION_ID, CURRENT_DATE, "CLOSED")));

        assertThrows(ResourceConflictException.class,
                () -> service.open(PHYSICAL_ID, request("open-2", null)));
        verify(custody, never()).issueTableFloat(any(), any());
    }

    @Test
    void conflictingOpenOperationIsRejectedBeforeCustody() {
        allow(Role.SUPER_ADMIN);
        when(physicalTables.findByIdForUpdate(PHYSICAL_ID)).thenReturn(Optional.of(physical()));
        when(operations.findByPhysicalTableIdAndBusinessDate(PHYSICAL_ID, CURRENT_DATE))
                .thenReturn(Optional.empty());
        when(operations.findFirstByPhysicalTableIdAndStatusIgnoreCase(PHYSICAL_ID, "OPEN"))
                .thenReturn(Optional.of(operation(HISTORICAL_ID, CURRENT_DATE.minusDays(1), "OPEN")));

        assertThrows(ResourceConflictException.class,
                () -> service.open(PHYSICAL_ID, request("open-3", null)));
        verify(custody, never()).issueTableFloat(any(), any());
    }

    @Test
    void exactIdempotentRetryReturnsOriginalAndDoesNotWriteAgain() {
        allow(Role.SUPER_ADMIN);
        PitTable original = operation(OPERATION_ID, CURRENT_DATE, "OPEN");
        original.setRemarks("Daily opening");
        when(operations.findByOpeningIdempotencyKey("open-4")).thenReturn(Optional.of(original));

        var response = service.open(PHYSICAL_ID, request("open-4", "Daily opening"));

        assertEquals(OPERATION_ID, response.id());
        verify(custody).validateTableFloatIssueReplay(
                org.mockito.ArgumentMatchers.eq(OPERATION_ID), any(ChipCustodyTransferRequest.class));
        verify(operations, never()).saveAndFlush(any());
        verify(custody, never()).issueTableFloat(any(), any());
        verify(audit, never()).log(any(), any(), any(), any(), any());
    }

    @Test
    void changedRequestWithSameIdempotencyKeyIsRejected() {
        allow(Role.SUPER_ADMIN);
        PitTable original = operation(OPERATION_ID, CURRENT_DATE, "OPEN");
        original.setRemarks("Original");
        when(operations.findByOpeningIdempotencyKey("open-5")).thenReturn(Optional.of(original));

        assertThrows(ResourceConflictException.class,
                () -> service.open(PHYSICAL_ID, request("open-5", "Changed")));
        verify(custody, never()).issueTableFloat(any(), any());
    }

    @Test
    void systemLockPreventsOperationAndCustodyWrites() {
        allow(Role.SUPER_ADMIN);
        when(systemLock.isSystemLocked()).thenReturn(true);

        assertThrows(ResourceConflictException.class,
                () -> service.open(PHYSICAL_ID, request("open-6", null)));
        verify(operations, never()).saveAndFlush(any());
        verify(custody, never()).issueTableFloat(any(), any());
    }

    @Test
    void custodyFailurePropagatesBeforeOpeningAudit() {
        allow(Role.SUPER_ADMIN);
        when(physicalTables.findByIdForUpdate(PHYSICAL_ID)).thenReturn(Optional.of(physical()));
        when(operations.findByPhysicalTableIdAndBusinessDate(PHYSICAL_ID, CURRENT_DATE))
                .thenReturn(Optional.empty());
        when(operations.findFirstByPhysicalTableIdAndStatusIgnoreCase(PHYSICAL_ID, "OPEN"))
                .thenReturn(Optional.empty());
        when(custody.calculateDenominationTotal(Map.of(5000, 20L)))
                .thenReturn(new BigDecimal("100000"));
        when(operations.saveAndFlush(any())).thenAnswer(invocation -> {
            PitTable value = invocation.getArgument(0);
            value.setId(OPERATION_ID);
            return value;
        });
        org.mockito.Mockito.doThrow(new ResourceConflictException("Insufficient cage inventory."))
                .when(custody).issueTableFloat(org.mockito.ArgumentMatchers.eq(OPERATION_ID), any());

        assertThrows(ResourceConflictException.class,
                () -> service.open(PHYSICAL_ID, request("open-insufficient", null)));

        verify(audit, never()).log(any(), any(), any(), any(), any());
    }

    @Test
    void dealerCannotExecuteCombinedOpeningOperation() {
        when(currentRole.getCurrentRole()).thenReturn(Optional.of(Role.DEALER));
        when(permissions.canOpenPitTableOperation(Role.DEALER)).thenReturn(false);

        assertThrows(RuntimeException.class,
                () -> service.open(PHYSICAL_ID, request("open-7", null)));
        verify(physicalTables, never()).findByIdForUpdate(any());
    }

    @Test
    void overviewReportsPhysicalTableWithoutCurrentOperationAsNotOpened() {
        when(businessDates.getCurrentBusinessDate()).thenReturn(CURRENT_DATE);
        when(physicalTables.findAllByOrderByTableCodeAsc()).thenReturn(List.of(physical()));
        when(operations.findByBusinessDate(CURRENT_DATE)).thenReturn(List.of());

        var response = service.overview().getFirst();

        assertEquals(PHYSICAL_ID, response.physicalTableId());
        assertEquals("NOT_OPENED", response.status());
        assertEquals(null, response.operationId());
        assertEquals(null, response.activeDealer());
        assertEquals(null, response.activeSupervisor());
        verify(staffAssignments, never()).findActiveStaffForOverview(any());
    }

    @Test
    void dealerOverviewContainsOnlyAssignedOperationAndNoNotOpenedTables() {
        PhysicalPitTable assignedPhysical = physical();
        PhysicalPitTable notOpened = physical();
        notOpened.setId(UUID.randomUUID());
        notOpened.setTableCode("BAC-002");
        PitTable assigned = operation(OPERATION_ID, CURRENT_DATE, "OPEN");
        when(businessDates.getCurrentBusinessDate()).thenReturn(CURRENT_DATE);
        when(physicalTables.findAllByOrderByTableCodeAsc()).thenReturn(List.of(assignedPhysical, notOpened));
        when(operations.findByBusinessDate(CURRENT_DATE)).thenReturn(List.of(assigned));
        when(tableAccess.isDealer()).thenReturn(true);
        when(tableAccess.currentDealerOperationId()).thenReturn(Optional.of(OPERATION_ID));

        var response = service.overview();

        assertEquals(1, response.size());
        assertEquals(OPERATION_ID, response.getFirst().operationId());
    }

    @Test
    void unassignedDealerOverviewIsEmpty() {
        when(businessDates.getCurrentBusinessDate()).thenReturn(CURRENT_DATE);
        when(physicalTables.findAllByOrderByTableCodeAsc()).thenReturn(List.of(physical()));
        when(tableAccess.isDealer()).thenReturn(true);
        when(tableAccess.currentDealerOperationId()).thenReturn(Optional.empty());

        assertEquals(List.of(), service.overview());
    }

    @Test
    void overviewUsesCurrentBusinessDateOperationRatherThanHistoricalLatest() {
        PitTable current = operation(OPERATION_ID, CURRENT_DATE, "OPEN");
        when(businessDates.getCurrentBusinessDate()).thenReturn(CURRENT_DATE);
        when(physicalTables.findAllByOrderByTableCodeAsc()).thenReturn(List.of(physical()));
        when(operations.findByBusinessDate(CURRENT_DATE)).thenReturn(List.of(current));
        when(staffAssignments.findActiveStaffForOverview(List.of(OPERATION_ID))).thenReturn(List.of());

        var response = service.overview().getFirst();

        assertEquals(OPERATION_ID, response.operationId());
        assertEquals(CURRENT_DATE, response.businessDate());
        verify(operations).findByBusinessDate(CURRENT_DATE);
        verify(operations, never()).findByPhysicalTableIdAndBusinessDate(any(), any());
        verify(reads).summarize(List.of(OPERATION_ID));
        verify(operations, never()).findByPhysicalTableIdOrderByBusinessDateDesc(PHYSICAL_ID);
        verify(staffAssignments).findActiveStaffForOverview(List.of(OPERATION_ID));
    }

    @Test
    void overviewReturnsSafeActiveDealerAndSupervisorSummariesWithoutChangingFinancialFields() {
        PitTable current = operation(OPERATION_ID, CURRENT_DATE, "OPEN");
        when(businessDates.getCurrentBusinessDate()).thenReturn(CURRENT_DATE);
        when(physicalTables.findAllByOrderByTableCodeAsc()).thenReturn(List.of(physical()));
        when(operations.findByBusinessDate(CURRENT_DATE)).thenReturn(List.of(current));
        LocalDateTime dealerStarted = LocalDateTime.of(2026, 9, 2, 10, 0);
        LocalDateTime supervisorStarted = LocalDateTime.of(2026, 9, 2, 9, 30);
        PitTableActiveStaffProjection dealer =
                staffRow(OPERATION_ID, DEALER_ASSIGNMENT_ID, DEALER_USER_ID, "dealer",
                        "Development Dealer", com.casino.casinoerp.entity.PitTableStaffAssignmentRole.DEALER,
                        dealerStarted);
        PitTableActiveStaffProjection supervisor =
                staffRow(OPERATION_ID, SUPERVISOR_ASSIGNMENT_ID, SUPERVISOR_USER_ID, "pitsupervisor",
                        "Development Pit Supervisor",
                        com.casino.casinoerp.entity.PitTableStaffAssignmentRole.PIT_SUPERVISOR,
                        supervisorStarted);
        when(staffAssignments.findActiveStaffForOverview(List.of(OPERATION_ID)))
                .thenReturn(List.of(dealer, supervisor));

        var response = service.overview().getFirst();

        assertEquals(DEALER_ASSIGNMENT_ID, response.activeDealer().assignmentId());
        assertEquals(DEALER_USER_ID, response.activeDealer().userId());
        assertEquals("dealer", response.activeDealer().username());
        assertEquals("Development Dealer", response.activeDealer().displayName());
        assertEquals(dealerStarted, response.activeDealer().startedAt());
        assertEquals(SUPERVISOR_ASSIGNMENT_ID, response.activeSupervisor().assignmentId());
        assertEquals(SUPERVISOR_USER_ID, response.activeSupervisor().userId());
        assertEquals("pitsupervisor", response.activeSupervisor().username());
        assertEquals(supervisorStarted, response.activeSupervisor().startedAt());
        assertEquals(new BigDecimal("100000"), response.openingFloat());
        assertEquals(BigDecimal.ZERO, response.chipIn());
        assertEquals(BigDecimal.ZERO, response.verifiedWins());
        assertEquals(BigDecimal.ZERO, response.verifiedLosses());
        assertEquals(BigDecimal.ZERO, response.netPosition());
    }

    @Test
    void overviewSupportsEitherRoleOrNoActiveStaff() {
        PitTable current = operation(OPERATION_ID, CURRENT_DATE, "OPEN");
        stubOverview(current);
        PitTableActiveStaffProjection dealer =
                staffRow(OPERATION_ID, DEALER_ASSIGNMENT_ID, DEALER_USER_ID, "dealer", null,
                        com.casino.casinoerp.entity.PitTableStaffAssignmentRole.DEALER,
                        LocalDateTime.of(2026, 9, 2, 10, 0));
        when(staffAssignments.findActiveStaffForOverview(List.of(OPERATION_ID)))
                .thenReturn(List.of(dealer));
        var dealerOnly = service.overview().getFirst();
        assertEquals(DEALER_ASSIGNMENT_ID, dealerOnly.activeDealer().assignmentId());
        assertEquals(null, dealerOnly.activeSupervisor());

        PitTableActiveStaffProjection supervisor =
                staffRow(OPERATION_ID, SUPERVISOR_ASSIGNMENT_ID, SUPERVISOR_USER_ID, "pitsupervisor", null,
                        com.casino.casinoerp.entity.PitTableStaffAssignmentRole.PIT_SUPERVISOR,
                        LocalDateTime.of(2026, 9, 2, 9, 30));
        when(staffAssignments.findActiveStaffForOverview(List.of(OPERATION_ID)))
                .thenReturn(List.of(supervisor));
        var supervisorOnly = service.overview().getFirst();
        assertEquals(null, supervisorOnly.activeDealer());
        assertEquals(SUPERVISOR_ASSIGNMENT_ID, supervisorOnly.activeSupervisor().assignmentId());

        when(staffAssignments.findActiveStaffForOverview(List.of(OPERATION_ID))).thenReturn(List.of());
        var noStaff = service.overview().getFirst();
        assertEquals(null, noStaff.activeDealer());
        assertEquals(null, noStaff.activeSupervisor());
    }

    @Test
    void overviewBatchesStaffForAllOperationsAndKeepsAssignmentsWithTheirOperation() {
        UUID secondPhysicalId = UUID.fromString("10000000-0000-0000-0000-000000000002");
        UUID secondOperationId = UUID.fromString("20000000-0000-0000-0000-000000000002");
        PhysicalPitTable secondPhysical = physical();
        secondPhysical.setId(secondPhysicalId);
        secondPhysical.setTableCode("BAC-002");
        PitTable secondOperation = operation(secondOperationId, CURRENT_DATE, "OPEN");
        secondOperation.setPhysicalTableId(secondPhysicalId);
        secondOperation.setTableCode("BAC-002");
        when(businessDates.getCurrentBusinessDate()).thenReturn(CURRENT_DATE);
        when(physicalTables.findAllByOrderByTableCodeAsc()).thenReturn(List.of(physical(), secondPhysical));
        when(operations.findByBusinessDate(CURRENT_DATE))
                .thenReturn(List.of(operation(OPERATION_ID, CURRENT_DATE, "OPEN"), secondOperation));
        PitTableActiveStaffProjection firstDealer =
                staffRow(OPERATION_ID, DEALER_ASSIGNMENT_ID, DEALER_USER_ID, "dealer-a", null,
                                com.casino.casinoerp.entity.PitTableStaffAssignmentRole.DEALER,
                                LocalDateTime.of(2026, 9, 2, 10, 0));
        PitTableActiveStaffProjection secondSupervisor =
                staffRow(secondOperationId, SUPERVISOR_ASSIGNMENT_ID, SUPERVISOR_USER_ID,
                                "supervisor-b", null,
                                com.casino.casinoerp.entity.PitTableStaffAssignmentRole.PIT_SUPERVISOR,
                                LocalDateTime.of(2026, 9, 2, 10, 0));
        when(staffAssignments.findActiveStaffForOverview(List.of(OPERATION_ID, secondOperationId)))
                .thenReturn(List.of(firstDealer, secondSupervisor));

        var response = service.overview();

        assertEquals("dealer-a", response.get(0).activeDealer().username());
        assertEquals(null, response.get(0).activeSupervisor());
        assertEquals(null, response.get(1).activeDealer());
        assertEquals("supervisor-b", response.get(1).activeSupervisor().username());
        verify(staffAssignments, times(1))
                .findActiveStaffForOverview(List.of(OPERATION_ID, secondOperationId));
    }

    @Test void completedOpeningReplaySurvivesDateRolloverWithoutNewCustody() {
        allow(Role.SUPER_ADMIN);
        PitTable completed=operation(OPERATION_ID,CURRENT_DATE,"CLOSED");
        when(operations.findByOpeningIdempotencyKey("completed")).thenReturn(Optional.of(completed));
        org.mockito.Mockito.lenient().when(businessDates.getCurrentBusinessDate()).thenReturn(CURRENT_DATE.plusDays(1));
        service.open(PHYSICAL_ID,request("completed",null));
        verify(businessDates).lockLifecycleForPitOpening();
        verify(businessDates,never()).validateNewOperationalMutationAllowed();
        verify(operations,never()).saveAndFlush(any());
        verify(custody,never()).issueTableFloat(any(),any());
        verify(custody).validateTableFloatIssueReplay(org.mockito.ArgumentMatchers.eq(OPERATION_ID),any());
    }

    @Test void staleOpeningDateRejectsBeforeAnyCustodyWrite() {
        allow(Role.SUPER_ADMIN);
        var stale = new OpenPitTableOperationRequest(Map.of(5000, 20L), null, "stale", CURRENT_DATE.minusDays(1));
        assertThrows(ResourceConflictException.class, () -> service.open(PHYSICAL_ID, stale));
        verify(operations, never()).saveAndFlush(any());
        org.mockito.Mockito.verifyNoInteractions(custody);
    }

    @Test void overviewUsesBulkAggregateValuesIncludingResultsFromLeftPlayers() {
        stubOverview(operation(OPERATION_ID, CURRENT_DATE, "OPEN"));
        when(reads.summarize(List.of(OPERATION_ID))).thenReturn(Map.of(OPERATION_ID,
                new PitReadRepository.Totals(1, new BigDecimal("2500"), new BigDecimal("500"), new BigDecimal("1000"))));
        var value = service.overview().getFirst();
        assertEquals(1, value.currentPlayers());
        assertEquals(new BigDecimal("2500"), value.chipIn());
        assertEquals(new BigDecimal("500"), value.verifiedWins());
        assertEquals(new BigDecimal("500"), value.netPosition());
        verify(reads).summarize(List.of(OPERATION_ID));
        verify(operations, never()).findByPhysicalTableIdAndBusinessDate(any(), any());
    }

    private void stubOverview(PitTable current) {
        when(businessDates.getCurrentBusinessDate()).thenReturn(CURRENT_DATE);
        when(physicalTables.findAllByOrderByTableCodeAsc()).thenReturn(List.of(physical()));
        when(operations.findByBusinessDate(CURRENT_DATE)).thenReturn(List.of(current));
    }

    private PitTableActiveStaffProjection staffRow(
            UUID operationId, UUID assignmentId, UUID userId, String username, String displayName,
            com.casino.casinoerp.entity.PitTableStaffAssignmentRole role, LocalDateTime startedAt) {
        PitTableActiveStaffProjection row = mock(PitTableActiveStaffProjection.class);
        when(row.getPitTableId()).thenReturn(operationId);
        when(row.getAssignmentId()).thenReturn(assignmentId);
        when(row.getUserId()).thenReturn(userId);
        when(row.getUsername()).thenReturn(username);
        when(row.getDisplayName()).thenReturn(displayName);
        when(row.getAssignmentRole()).thenReturn(role);
        when(row.getStartedAt()).thenReturn(startedAt);
        return row;
    }

    private void allow(Role role) {
        when(currentRole.getCurrentRole()).thenReturn(Optional.of(role));
        when(permissions.canOpenPitTableOperation(role)).thenReturn(true);
        org.mockito.Mockito.lenient().when(systemLock.isSystemLocked()).thenReturn(false);
        org.mockito.Mockito.lenient().when(businessDates.getCurrentBusinessDate()).thenReturn(CURRENT_DATE);
        User actor = new User();
        actor.setId(ACTOR_ID);
        org.mockito.Mockito.lenient().when(authenticatedUser.getRequiredUser()).thenReturn(actor);
    }

    private OpenPitTableOperationRequest request(String key, String remarks) {
        return new OpenPitTableOperationRequest(Map.of(5000, 20L), remarks, key, CURRENT_DATE);
    }

    private PhysicalPitTable physical() {
        PhysicalPitTable value = new PhysicalPitTable();
        value.setId(PHYSICAL_ID);
        value.setTableCode("BAC-001");
        value.setTableName("Baccarat Main Table");
        value.setGameType("Baccarat");
        value.setMaxPlayers(7);
        value.setStatus("ACTIVE");
        return value;
    }

    private PitTable operation(UUID id, LocalDate date, String status) {
        PitTable value = new PitTable();
        value.setId(id);
        value.setPhysicalTableId(PHYSICAL_ID);
        value.setTableCode("BAC-001");
        value.setTableName("Baccarat Main Table");
        value.setGameType("Baccarat");
        value.setBusinessDate(date);
        value.setStatus(status);
        value.setOpeningFloat(new BigDecimal("100000"));
        return value;
    }
}
