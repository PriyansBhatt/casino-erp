package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.LegacyPitTableReconciliationRequest;
import com.casino.casinoerp.dto.PitTableReconciliationResponse;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.exception.ResourceConflictException;
import com.casino.casinoerp.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class LegacyPitTableReconciliationService {
    private static final String LEGACY_UNVERIFIED = "LEGACY_UNVERIFIED";

    private final PitTableRepository pitTables;
    private final PitTableCustomerAssignmentRepository assignments;
    private final ChipCustodyMovementRepository custodyMovements;
    private final LegacyPitTableReconciliationResolutionRepository resolutions;
    private final BusinessDateService businessDates;
    private final SystemLockService systemLock;
    private final ChipCustodyService custody;
    private final CurrentUserRoleService currentUser;
    private final RolePermissionService permissions;
    private final AuditLogService audit;
    private final PitTableReconciliationService reconciliation;

    public LegacyPitTableReconciliationService(
            PitTableRepository pitTables,
            PitTableCustomerAssignmentRepository assignments,
            ChipCustodyMovementRepository custodyMovements,
            LegacyPitTableReconciliationResolutionRepository resolutions,
            BusinessDateService businessDates,
            SystemLockService systemLock,
            ChipCustodyService custody,
            CurrentUserRoleService currentUser,
            RolePermissionService permissions,
            AuditLogService audit,
            PitTableReconciliationService reconciliation) {
        this.pitTables = pitTables;
        this.assignments = assignments;
        this.custodyMovements = custodyMovements;
        this.resolutions = resolutions;
        this.businessDates = businessDates;
        this.systemLock = systemLock;
        this.custody = custody;
        this.currentUser = currentUser;
        this.permissions = permissions;
        this.audit = audit;
        this.reconciliation = reconciliation;
    }

    @Transactional
    public PitTableReconciliationResponse resolve(
            UUID tableId, LegacyPitTableReconciliationRequest request) {
        validateSuperAdmin();
        validateRequest(request);

        BigDecimal physicalClosingFloat = request.physicalClosingFloat();
        String reason = request.reason().trim();
        String idempotencyKey = request.idempotencyKey().trim();

        var replay = resolutions.findByIdempotencyKey(idempotencyKey);
        if (replay.isPresent()) {
            validateExactReplay(replay.get(), tableId, physicalClosingFloat, reason);
            PitTable table = pitTables.findById(tableId)
                    .orElseThrow(() -> new ResourceConflictException("Resolved Pit Table no longer exists."));
            return reconciliation.reconcile(table);
        }
        if (resolutions.findByPitTableId(tableId).isPresent()) {
            throw new ResourceConflictException("Pit Table already has a legacy reconciliation resolution.");
        }

        businessDates.validateSettlementMutationAllowed();
        businessDates.validateBusinessDateIsOpen();
        if (systemLock.isSystemLocked()) {
            throw new ResourceConflictException(
                    "System is locked. Legacy Pit Table reconciliation is not allowed.");
        }

        PitTable table = pitTables.findByIdForUpdate(tableId)
                .orElseThrow(() -> new ResourceConflictException("Pit table not found."));
        var concurrentResolution = resolutions.findByPitTableId(tableId);
        if (concurrentResolution.isPresent()) {
            validateExactReplay(concurrentResolution.get(), tableId, physicalClosingFloat, reason);
            return reconciliation.reconcile(table);
        }
        LocalDate currentBusinessDate = businessDates.getCurrentBusinessDate();
        if (!"OPEN".equalsIgnoreCase(table.getStatus())) {
            throw new ResourceConflictException("Only an OPEN Pit Table can be legacy resolved.");
        }
        if (!currentBusinessDate.equals(table.getBusinessDate())) {
            throw new ResourceConflictException(
                    "Pit Table does not belong to the current OPEN Business Date.");
        }
        if (!assignments.findByPitTableIdAndStatusOrderByJoinedAtAsc(
                tableId, PitTableCustomerAssignmentStatus.ACTIVE).isEmpty()) {
            throw new ResourceConflictException(
                    "All active customer assignments must leave the Pit Table before legacy resolution.");
        }

        custody.validateTableCustodySettled(tableId);
        if (custodyMovements.existsByMovementTypeAndPitTableId(
                ChipCustodyMovementType.TABLE_FLOAT_ISSUE, tableId)) {
            throw new ResourceConflictException(
                    "Pit Table has an authoritative TABLE_FLOAT_ISSUE and is not eligible for legacy resolution.");
        }
        validatePredatesCustodyTracking(table);

        UUID actorId = currentUser.getCurrentUserId();
        if (actorId == null) {
            throw new ResourceConflictException("Authenticated resolving user could not be identified.");
        }
        BigDecimal openingFloat = table.getOpeningFloat() == null
                ? BigDecimal.ZERO : table.getOpeningFloat();
        LocalDateTime resolvedAt = LocalDateTime.now();

        LegacyPitTableReconciliationResolution resolution = new LegacyPitTableReconciliationResolution();
        resolution.setPitTableId(tableId);
        resolution.setBusinessDate(table.getBusinessDate());
        resolution.setOriginalOpeningFloat(openingFloat);
        resolution.setPhysicalClosingFloat(physicalClosingFloat);
        resolution.setLegacyOpeningFloatGap(openingFloat.subtract(physicalClosingFloat));
        resolution.setOpeningFloatVerification(LEGACY_UNVERIFIED);
        resolution.setReason(reason);
        resolution.setResolvedBy(actorId);
        resolution.setResolvedAt(resolvedAt);
        resolution.setIdempotencyKey(idempotencyKey);
        resolutions.save(resolution);

        table.setClosingFloat(physicalClosingFloat);
        table.setClosedAt(resolvedAt);
        table.setStatus("CLOSED");
        PitTable savedTable = pitTables.save(table);

        audit.log(
                "LEGACY_PIT_TABLE_RECONCILIATION_RESOLVED", "PIT_TABLE", tableId, actorId,
                "Legacy Pit Table reconciliation resolved for " + table.getTableCode()
                        + "; businessDate=" + table.getBusinessDate()
                        + "; openingFloat=" + openingFloat
                        + "; physicalClosingFloat=" + physicalClosingFloat
                        + "; legacyGap=" + resolution.getLegacyOpeningFloatGap()
                        + "; reason=" + reason
                        + "; idempotencyKey=" + idempotencyKey);

        return reconciliation.reconcile(savedTable);
    }

    private void validateSuperAdmin() {
        if (!currentUser.getCurrentRole()
                .map(permissions::canResolveLegacyPitTableReconciliation)
                .orElse(false)) {
            throw new RuntimeException("Only Super Admin can resolve legacy Pit Table reconciliation.");
        }
    }

    private void validateRequest(LegacyPitTableReconciliationRequest request) {
        if (request == null || request.physicalClosingFloat() == null
                || request.physicalClosingFloat().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Physical closing float must be zero or greater.");
        }
        if (request.reason() == null || request.reason().trim().length() < 20
                || request.reason().trim().length() > 500) {
            throw new IllegalArgumentException(
                    "Legacy resolution reason must be between 20 and 500 characters.");
        }
        if (request.idempotencyKey() == null || request.idempotencyKey().isBlank()
                || request.idempotencyKey().trim().length() > 100) {
            throw new IllegalArgumentException("A valid idempotency key is required.");
        }
    }

    private void validatePredatesCustodyTracking(PitTable table) {
        ChipCustodyMovement firstMovement = custodyMovements.findFirstByOrderByCreatedAtAsc()
                .orElseThrow(() -> new ResourceConflictException(
                        "Authoritative custody-ledger introduction cannot be established from persisted data."));
        if (table.getOpenedAt() == null || !table.getOpenedAt().isBefore(firstMovement.getCreatedAt())) {
            throw new ResourceConflictException(
                    "Pit Table does not predate authoritative physical custody tracking.");
        }
    }

    private void validateExactReplay(
            LegacyPitTableReconciliationResolution existing,
            UUID tableId,
            BigDecimal physicalClosingFloat,
            String reason) {
        if (!existing.getPitTableId().equals(tableId)
                || existing.getPhysicalClosingFloat().compareTo(physicalClosingFloat) != 0
                || !existing.getReason().equals(reason)) {
            throw new ResourceConflictException(
                    "Idempotency key was already used for a different legacy reconciliation request.");
        }
    }
}
