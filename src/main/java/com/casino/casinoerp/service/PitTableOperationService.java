package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.exception.ResourceConflictException;
import com.casino.casinoerp.exception.ResourceNotFoundException;
import com.casino.casinoerp.repository.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class PitTableOperationService {
    private final PhysicalPitTableRepository physicalTables;
    private final PitTableRepository operations;
    private final PitTableCustomerAssignmentRepository assignments;
    private final VerifiedGamingResultRepository results;
    private final PitTableStaffAssignmentRepository staffAssignments;
    private final ChipCustodyMovementRepository custodyMovements;
    private final ChipCustodyService custody;
    private final BusinessDateService businessDates;
    private final SystemLockService systemLock;
    private final CurrentUserRoleService currentRole;
    private final RolePermissionService permissions;
    private final AuthenticatedUserService authenticatedUser;
    private final AuditLogService audit;
    private final PitTableAccessService tableAccess;

    public PitTableOperationService(
            PhysicalPitTableRepository physicalTables, PitTableRepository operations,
            PitTableCustomerAssignmentRepository assignments,
            VerifiedGamingResultRepository results,
            PitTableStaffAssignmentRepository staffAssignments,
            ChipCustodyMovementRepository custodyMovements, ChipCustodyService custody,
            BusinessDateService businessDates, SystemLockService systemLock,
            CurrentUserRoleService currentRole, RolePermissionService permissions,
            AuthenticatedUserService authenticatedUser, AuditLogService audit,
            PitTableAccessService tableAccess) {
        this.physicalTables = physicalTables;
        this.operations = operations;
        this.assignments = assignments;
        this.results = results;
        this.staffAssignments = staffAssignments;
        this.custodyMovements = custodyMovements;
        this.custody = custody;
        this.businessDates = businessDates;
        this.systemLock = systemLock;
        this.currentRole = currentRole;
        this.permissions = permissions;
        this.authenticatedUser = authenticatedUser;
        this.audit = audit;
        this.tableAccess = tableAccess;
    }

    @Transactional
    public PitTableResponse open(UUID physicalTableId, OpenPitTableOperationRequest request) {
        validateRole();
        if (systemLock.isSystemLocked()) {
            throw new ResourceConflictException("System is locked. Pit table operations are not allowed.");
        }
        businessDates.validateBusinessDateIsOpen();
        LocalDate businessDate = businessDates.getCurrentBusinessDate();
        String key = request.idempotencyKey().trim();
        String remarks = normalizeRemarks(request.remarks());
        ChipCustodyTransferRequest custodyRequest =
                new ChipCustodyTransferRequest(request.denominations(), key);

        PitTable replay = operations.findByOpeningIdempotencyKey(key).orElse(null);
        if (replay != null) {
            validateReplay(replay, physicalTableId, remarks, custodyRequest);
            return response(replay);
        }

        PhysicalPitTable physical = physicalTables.findByIdForUpdate(physicalTableId)
                .orElseThrow(() -> new ResourceNotFoundException("Physical Pit Table not found."));
        if (!"ACTIVE".equalsIgnoreCase(physical.getStatus())) {
            throw new ResourceConflictException("Physical Pit Table is not ACTIVE.");
        }
        replay = operations.findByOpeningIdempotencyKey(key).orElse(null);
        if (replay != null) {
            validateReplay(replay, physicalTableId, remarks, custodyRequest);
            return response(replay);
        }
        if (operations.findByPhysicalTableIdAndBusinessDate(physicalTableId, businessDate).isPresent()) {
            throw new ResourceConflictException(
                    "This physical Pit Table already has an operation for the current Business Date.");
        }
        if (operations.findFirstByPhysicalTableIdAndStatusIgnoreCase(physicalTableId, "OPEN").isPresent()) {
            throw new ResourceConflictException("This physical Pit Table already has an OPEN operation.");
        }

        BigDecimal openingFloat = custody.calculateDenominationTotal(request.denominations());
        PitTable operation = new PitTable();
        operation.setPhysicalTableId(physical.getId());
        operation.setTableCode(physical.getTableCode());
        operation.setTableName(physical.getTableName());
        operation.setGameType(physical.getGameType());
        operation.setMaxPlayers(physical.getMaxPlayers());
        operation.setBusinessDate(businessDate);
        operation.setStatus("OPEN");
        operation.setOpeningFloat(openingFloat);
        operation.setOpenedAt(LocalDateTime.now());
        operation.setRemarks(remarks);
        operation.setOpeningIdempotencyKey(key);

        PitTable saved;
        try {
            saved = operations.saveAndFlush(operation);
        } catch (DataIntegrityViolationException exception) {
            throw new ResourceConflictException(
                    "A Pit Table operation or opening idempotency key already exists.");
        }
        custody.issueTableFloat(saved.getId(), custodyRequest);

        UUID actorId = authenticatedUser.getRequiredUser().getId();
        audit.log("OPEN_PIT_TABLE_OPERATION", "PIT_TABLE", saved.getId(), actorId,
                "physicalTableId=" + physicalTableId + ", tableCode=" + physical.getTableCode()
                        + ", businessDate=" + businessDate + ", openingFloat=" + openingFloat
                        + ", denominations=" + request.denominations()
                        + ", idempotencyKey=" + key);
        return response(saved);
    }

    @Transactional(readOnly = true)
    public List<PitTableOverviewResponse> overview() {
        businessDates.validateBusinessDateIsOpen();
        LocalDate date = businessDates.getCurrentBusinessDate();
        List<PhysicalPitTable> physical = physicalTables.findAllByOrderByTableCodeAsc();
        Map<UUID, PitTable> operationByPhysicalTable = new HashMap<>();
        physical.forEach(table -> operations.findByPhysicalTableIdAndBusinessDate(table.getId(), date)
                .ifPresent(operation -> operationByPhysicalTable.put(table.getId(), operation)));

        List<UUID> operationIds = operationByPhysicalTable.values().stream()
                .map(PitTable::getId).toList();
        Map<UUID, Map<PitTableStaffAssignmentRole, PitTableActiveStaffSummary>> staffByOperation =
                activeStaffByOperation(operationIds);

        if (tableAccess.isDealer()) {
            UUID assignedOperationId = tableAccess.currentDealerOperationId().orElse(null);
            if (assignedOperationId == null) {
                return List.of();
            }
            return physical.stream()
                    .filter(table -> {
                        PitTable operation = operationByPhysicalTable.get(table.getId());
                        return operation != null && assignedOperationId.equals(operation.getId());
                    })
                    .map(table -> overview(table, date, operationByPhysicalTable.get(table.getId()), staffByOperation))
                    .toList();
        }

        return physical.stream().map(table -> overview(table, date,
                operationByPhysicalTable.get(table.getId()), staffByOperation)).toList();
    }

    @Transactional(readOnly = true)
    public List<PitTableResponse> history(UUID physicalTableId) {
        tableAccess.requireManagementAccess();
        if (!physicalTables.existsById(physicalTableId)) {
            throw new ResourceNotFoundException("Physical Pit Table not found.");
        }
        return operations.findByPhysicalTableIdOrderByBusinessDateDesc(physicalTableId)
                .stream().map(this::response).toList();
    }

    private PitTableOverviewResponse overview(
            PhysicalPitTable physical,
            LocalDate date,
            PitTable operation,
            Map<UUID, Map<PitTableStaffAssignmentRole, PitTableActiveStaffSummary>> staffByOperation) {
        if (operation == null) {
            return new PitTableOverviewResponse(physical.getId(), physical.getTableCode(),
                    physical.getTableName(), physical.getGameType(), physical.getMaxPlayers(),
                    physical.getStatus(), null, date, "NOT_OPENED", null,
                    0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    null, null);
        }
        long players = assignments.findByPitTableIdAndStatusOrderByJoinedAtAsc(
                operation.getId(), PitTableCustomerAssignmentStatus.ACTIVE).size();
        BigDecimal chipIn = custodyMovements.findByPitTableIdOrderByCreatedAtAsc(operation.getId())
                .stream().filter(value -> value.getMovementType() == ChipCustodyMovementType.CUSTOMER_TO_TABLE)
                .map(ChipCustodyMovement::getTotalValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<VerifiedGamingResult> tableResults = results.findByPitTableId(operation.getId());
        BigDecimal wins = tableResults.stream()
                .filter(value -> value.getResultType() == VerifiedGamingResultType.WIN)
                .map(VerifiedGamingResult::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal losses = tableResults.stream()
                .filter(value -> value.getResultType() == VerifiedGamingResultType.LOSS)
                .map(VerifiedGamingResult::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<PitTableStaffAssignmentRole, PitTableActiveStaffSummary> activeStaff =
                staffByOperation.getOrDefault(operation.getId(), Map.of());
        return new PitTableOverviewResponse(physical.getId(), physical.getTableCode(),
                physical.getTableName(), physical.getGameType(), physical.getMaxPlayers(),
                physical.getStatus(), operation.getId(), operation.getBusinessDate(),
                operation.getStatus(), operation.getOpeningFloat(), players, chipIn,
                wins, losses, losses.subtract(wins),
                activeStaff.get(PitTableStaffAssignmentRole.DEALER),
                activeStaff.get(PitTableStaffAssignmentRole.PIT_SUPERVISOR));
    }

    private Map<UUID, Map<PitTableStaffAssignmentRole, PitTableActiveStaffSummary>> activeStaffByOperation(
            List<UUID> operationIds) {
        if (operationIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Map<PitTableStaffAssignmentRole, PitTableActiveStaffSummary>> grouped = new HashMap<>();
        for (PitTableActiveStaffProjection row : staffAssignments.findActiveStaffForOverview(operationIds)) {
            PitTableActiveStaffSummary summary = new PitTableActiveStaffSummary(
                    row.getAssignmentId(), row.getUserId(), row.getUsername(), row.getDisplayName(),
                    row.getAssignmentRole(), row.getStartedAt());
            grouped.computeIfAbsent(row.getPitTableId(), ignored -> new HashMap<>())
                    .putIfAbsent(row.getAssignmentRole(), summary);
        }
        return grouped;
    }

    private void validateReplay(PitTable replay, UUID physicalTableId, String remarks,
            ChipCustodyTransferRequest custodyRequest) {
        if (!physicalTableId.equals(replay.getPhysicalTableId())
                || !Objects.equals(remarks, normalizeRemarks(replay.getRemarks()))) {
            throw new ResourceConflictException(
                    "Idempotency key has already been used for a different Pit Table opening.");
        }
        custody.validateTableFloatIssueReplay(replay.getId(), custodyRequest);
    }

    private void validateRole() {
        if (!currentRole.getCurrentRole().map(permissions::canOpenPitTableOperation).orElse(false)) {
            throw new RuntimeException(
                    "Access denied. Only Pit Supervisor or Super Admin can open a Pit Table operation.");
        }
    }

    private String normalizeRemarks(String remarks) {
        return remarks == null || remarks.trim().isEmpty() ? null : remarks.trim();
    }

    private PitTableResponse response(PitTable table) {
        return new PitTableResponse(table.getId(), table.getTableCode(), table.getTableName(),
                table.getGameType(), table.getStatus(), table.getBusinessDate(), table.getOpenedAt(),
                table.getClosedAt(), table.getOpeningFloat(), table.getClosingFloat(), table.getRemarks());
    }
}
