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
import com.casino.casinoerp.repository.VerifiedGamingResultRepository;
import com.casino.casinoerp.security.Role;
import com.casino.casinoerp.service.AuditLogService;
import com.casino.casinoerp.service.AuthenticatedUserService;
import com.casino.casinoerp.service.BusinessDateService;
import com.casino.casinoerp.service.ChipCustodyService;
import com.casino.casinoerp.service.CurrentUserRoleService;
import com.casino.casinoerp.service.PitTableOperationService;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PitTableOperationServiceTests {
    private static final UUID PHYSICAL_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OPERATION_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID HISTORICAL_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final LocalDate CURRENT_DATE = LocalDate.of(2026, 9, 2);

    @Mock PhysicalPitTableRepository physicalTables;
    @Mock PitTableRepository operations;
    @Mock PitTableCustomerAssignmentRepository assignments;
    @Mock VerifiedGamingResultRepository results;
    @Mock ChipCustodyMovementRepository custodyMovements;
    @Mock ChipCustodyService custody;
    @Mock BusinessDateService businessDates;
    @Mock SystemLockService systemLock;
    @Mock CurrentUserRoleService currentRole;
    @Mock RolePermissionService permissions;
    @Mock AuthenticatedUserService authenticatedUser;
    @Mock AuditLogService audit;

    private PitTableOperationService service;

    @BeforeEach
    void setUp() {
        service = new PitTableOperationService(physicalTables, operations, assignments,
                results, custodyMovements, custody, businessDates, systemLock, currentRole,
                permissions, authenticatedUser, audit);
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
        when(operations.findByPhysicalTableIdAndBusinessDate(PHYSICAL_ID, CURRENT_DATE))
                .thenReturn(Optional.empty());

        var response = service.overview().getFirst();

        assertEquals(PHYSICAL_ID, response.physicalTableId());
        assertEquals("NOT_OPENED", response.status());
        assertEquals(null, response.operationId());
    }

    @Test
    void overviewUsesCurrentBusinessDateOperationRatherThanHistoricalLatest() {
        PitTable current = operation(OPERATION_ID, CURRENT_DATE, "OPEN");
        when(businessDates.getCurrentBusinessDate()).thenReturn(CURRENT_DATE);
        when(physicalTables.findAllByOrderByTableCodeAsc()).thenReturn(List.of(physical()));
        when(operations.findByPhysicalTableIdAndBusinessDate(PHYSICAL_ID, CURRENT_DATE))
                .thenReturn(Optional.of(current));
        when(assignments.findByPitTableIdAndStatusOrderByJoinedAtAsc(
                org.mockito.ArgumentMatchers.eq(OPERATION_ID), any())).thenReturn(List.of());
        when(custodyMovements.findByPitTableIdOrderByCreatedAtAsc(OPERATION_ID)).thenReturn(List.of());
        when(results.findByPitTableId(OPERATION_ID)).thenReturn(List.of());

        var response = service.overview().getFirst();

        assertEquals(OPERATION_ID, response.operationId());
        assertEquals(CURRENT_DATE, response.businessDate());
        verify(operations).findByPhysicalTableIdAndBusinessDate(PHYSICAL_ID, CURRENT_DATE);
        verify(operations, never()).findByPhysicalTableIdOrderByBusinessDateDesc(PHYSICAL_ID);
    }

    private void allow(Role role) {
        when(currentRole.getCurrentRole()).thenReturn(Optional.of(role));
        when(permissions.canOpenPitTableOperation(role)).thenReturn(true);
        when(systemLock.isSystemLocked()).thenReturn(false);
        org.mockito.Mockito.lenient().when(businessDates.getCurrentBusinessDate()).thenReturn(CURRENT_DATE);
        User actor = new User();
        actor.setId(ACTOR_ID);
        org.mockito.Mockito.lenient().when(authenticatedUser.getRequiredUser()).thenReturn(actor);
    }

    private OpenPitTableOperationRequest request(String key, String remarks) {
        return new OpenPitTableOperationRequest(Map.of(5000, 20L), remarks, key);
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
