package com.casino.casinoerp;

import com.casino.casinoerp.dto.LegacyPitTableReconciliationRequest;
import com.casino.casinoerp.dto.PitTableReconciliationResponse;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.exception.ResourceConflictException;
import com.casino.casinoerp.repository.*;
import com.casino.casinoerp.security.Role;
import com.casino.casinoerp.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LegacyPitTableReconciliationServiceTests {
    private final PitTableRepository tables = mock(PitTableRepository.class);
    private final PitTableCustomerAssignmentRepository assignments = mock(PitTableCustomerAssignmentRepository.class);
    private final ChipCustodyMovementRepository movements = mock(ChipCustodyMovementRepository.class);
    private final LegacyPitTableReconciliationResolutionRepository resolutions = mock(LegacyPitTableReconciliationResolutionRepository.class);
    private final BusinessDateService dates = mock(BusinessDateService.class);
    private final SystemLockService lock = mock(SystemLockService.class);
    private final ChipCustodyService custody = mock(ChipCustodyService.class);
    private final CurrentUserRoleService currentUser = mock(CurrentUserRoleService.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final PitTableReconciliationService reconciliation = mock(PitTableReconciliationService.class);
    private final LegacyPitTableReconciliationService service = new LegacyPitTableReconciliationService(
            tables, assignments, movements, resolutions, dates, lock, custody, currentUser,
            new RolePermissionService(), audit, reconciliation);

    private final UUID tableId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final LocalDate businessDate = LocalDate.of(2026, 8, 8);
    private PitTable table;
    private LegacyPitTableReconciliationRequest request;

    @BeforeEach
    void setUp() {
        table = new PitTable();
        table.setId(tableId);
        table.setTableCode("T-BAC-22");
        table.setStatus("OPEN");
        table.setBusinessDate(businessDate);
        table.setOpeningFloat(new BigDecimal("100000"));
        table.setOpenedAt(LocalDateTime.of(2026, 8, 17, 20, 53));
        request = new LegacyPitTableReconciliationRequest(BigDecimal.ZERO,
                "Legacy table predates authoritative physical custody tracking.", "legacy-table-1");

        when(currentUser.getCurrentRole()).thenReturn(Optional.of(Role.SUPER_ADMIN));
        when(currentUser.getCurrentUserId()).thenReturn(actorId);
        when(dates.getCurrentBusinessDate()).thenReturn(businessDate);
        when(tables.findByIdForUpdate(tableId)).thenReturn(Optional.of(table));
        when(tables.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(assignments.findByPitTableIdAndStatusOrderByJoinedAtAsc(
                tableId, PitTableCustomerAssignmentStatus.ACTIVE)).thenReturn(List.of());
        ChipCustodyMovement firstMovement = new ChipCustodyMovement();
        firstMovement.setCreatedAt(table.getOpenedAt().plusDays(1));
        when(movements.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(firstMovement));
        when(reconciliation.reconcile(any())).thenReturn(new PitTableReconciliationResponse(
                tableId, "T-BAC-22", new BigDecimal("100000"), BigDecimal.ZERO,
                null, "LEGACY_RESOLVED", "LEGACY_UNVERIFIED", new BigDecimal("100000"),
                request.reason(), actorId, LocalDateTime.now(), request.idempotencyKey()));
    }

    @Test
    void validResolutionClosesTableWithoutCreatingCustodyMovement() {
        PitTableReconciliationResponse response = service.resolve(tableId, request);

        assertThat(response.tableStatus()).isEqualTo("LEGACY_RESOLVED");
        assertThat(response.tableDifference()).isNull();
        assertThat(table.getOpeningFloat()).isEqualByComparingTo("100000");
        assertThat(table.getClosingFloat()).isZero();
        assertThat(table.getStatus()).isEqualTo("CLOSED");
        verify(custody).validateTableCustodySettled(tableId);
        verify(movements, never()).save(any());
        verify(resolutions).save(argThat(saved ->
                saved.getLegacyOpeningFloatGap().compareTo(new BigDecimal("100000")) == 0
                        && "LEGACY_UNVERIFIED".equals(saved.getOpeningFloatVerification())));
        verify(audit).log(eq("LEGACY_PIT_TABLE_RECONCILIATION_RESOLVED"), eq("PIT_TABLE"),
                eq(tableId), eq(actorId), contains("legacyGap=100000"));
    }

    @Test
    void nonSuperAdminRejectedByService() {
        when(currentUser.getCurrentRole()).thenReturn(Optional.of(Role.DIRECTOR));
        assertThatThrownBy(() -> service.resolve(tableId, request)).hasMessageContaining("Only Super Admin");
        verify(tables, never()).save(any());
    }

    @Test
    void activeAssignmentRejected() {
        when(assignments.findByPitTableIdAndStatusOrderByJoinedAtAsc(
                tableId, PitTableCustomerAssignmentStatus.ACTIVE))
                .thenReturn(List.of(new PitTableCustomerAssignment()));
        assertThatThrownBy(() -> service.resolve(tableId, request)).hasMessageContaining("active customer assignments");
    }

    @Test
    void nonzeroCustodyRejected() {
        doThrow(new ResourceConflictException("physical Pit Table chips remain"))
                .when(custody).validateTableCustodySettled(tableId);
        assertThatThrownBy(() -> service.resolve(tableId, request)).hasMessageContaining("physical Pit Table chips");
    }

    @Test
    void tableWithAuthoritativeFloatIssueRejected() {
        when(movements.existsByMovementTypeAndPitTableId(ChipCustodyMovementType.TABLE_FLOAT_ISSUE, tableId))
                .thenReturn(true);
        assertThatThrownBy(() -> service.resolve(tableId, request)).hasMessageContaining("TABLE_FLOAT_ISSUE");
    }

    @Test
    void modernTableRejected() {
        ChipCustodyMovement first = new ChipCustodyMovement();
        first.setCreatedAt(table.getOpenedAt().minusDays(1));
        when(movements.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(first));
        assertThatThrownBy(() -> service.resolve(tableId, request)).hasMessageContaining("does not predate");
    }

    @Test
    void systemLockAndWrongBusinessDateAreRejected() {
        when(lock.isSystemLocked()).thenReturn(true);
        assertThatThrownBy(() -> service.resolve(tableId, request)).hasMessageContaining("System is locked");
        when(lock.isSystemLocked()).thenReturn(false);
        table.setBusinessDate(businessDate.minusDays(1));
        assertThatThrownBy(() -> service.resolve(tableId, request)).hasMessageContaining("current OPEN Business Date");
    }

    @Test
    void negativeClosingFloatAndInvalidReasonAreRejectedBeforePersistence() {
        var negative = new LegacyPitTableReconciliationRequest(new BigDecimal("-1"),
                request.reason(), "legacy-table-negative");
        assertThatThrownBy(() -> service.resolve(tableId, negative))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("zero or greater");

        var blankReason = new LegacyPitTableReconciliationRequest(BigDecimal.ZERO,
                "   ", "legacy-table-blank-reason");
        assertThatThrownBy(() -> service.resolve(tableId, blankReason))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("reason");
        verify(tables, never()).save(any());
        verify(resolutions, never()).save(any());
    }

    @Test
    void absenceOfPersistedCustodyChronologyCannotBeTreatedAsLegacyEvidence() {
        when(movements.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.resolve(tableId, request))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("cannot be established");
        verify(tables, never()).save(any());
    }

    @Test
    void exactIdempotentRetryReturnsOriginalAndConflictIsRejected() {
        LegacyPitTableReconciliationResolution existing = existingResolution();
        when(resolutions.findByIdempotencyKey("legacy-table-1")).thenReturn(Optional.of(existing));
        when(tables.findById(tableId)).thenReturn(Optional.of(table));

        assertThat(service.resolve(tableId, request).tableStatus()).isEqualTo("LEGACY_RESOLVED");
        verify(tables, never()).save(any());
        verify(resolutions, never()).save(any());

        LegacyPitTableReconciliationRequest conflict = new LegacyPitTableReconciliationRequest(
                BigDecimal.ONE, request.reason(), "legacy-table-1");
        assertThatThrownBy(() -> service.resolve(tableId, conflict)).hasMessageContaining("different");
    }

    private LegacyPitTableReconciliationResolution existingResolution() {
        LegacyPitTableReconciliationResolution existing = new LegacyPitTableReconciliationResolution();
        existing.setPitTableId(tableId);
        existing.setPhysicalClosingFloat(BigDecimal.ZERO);
        existing.setReason(request.reason());
        existing.setIdempotencyKey(request.idempotencyKey());
        return existing;
    }
}
