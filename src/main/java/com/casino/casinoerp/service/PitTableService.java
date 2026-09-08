package com.casino.casinoerp.service;

import com.casino.casinoerp.entity.PitTable;
import com.casino.casinoerp.repository.PitTableRepository;
import com.casino.casinoerp.repository.PitTableCustomerAssignmentRepository;
import com.casino.casinoerp.entity.PitTableCustomerAssignmentStatus;
import com.casino.casinoerp.dto.CreatePitTableRequest;
import com.casino.casinoerp.exception.ResourceConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import java.util.List;

@Service
public class PitTableService {

    private final PitTableRepository repository;
    private final BusinessDateService businessDateService;
    private final SystemLockService systemLockService;
    private final AuditLogService auditLogService;
    private final CurrentUserRoleService currentUserRoleService;
    private final RolePermissionService rolePermissionService;
    private final PitTableCustomerAssignmentRepository assignmentRepository;
    private final ChipCustodyService chipCustodyService;
    private final PitTableAccessService tableAccess;

    public PitTableService(
            PitTableRepository repository,
            BusinessDateService businessDateService,
            SystemLockService systemLockService,
            AuditLogService auditLogService,
            CurrentUserRoleService currentUserRoleService,
            RolePermissionService rolePermissionService,
            PitTableCustomerAssignmentRepository assignmentRepository,
            ChipCustodyService chipCustodyService, PitTableAccessService tableAccess) {

        this.repository = repository;
        this.businessDateService = businessDateService;
        this.systemLockService = systemLockService;
        this.auditLogService = auditLogService;
        this.currentUserRoleService = currentUserRoleService;
        this.rolePermissionService = rolePermissionService;
        this.assignmentRepository = assignmentRepository;
        this.chipCustodyService = chipCustodyService;
        this.tableAccess = tableAccess;
    }

    private void validatePitRole() {

        if (!rolePermissionService.canPitTransaction(currentUserRoleService.getCurrentUserRole())) {

            throw new RuntimeException(
                    "Only Dealer, Pit Supervisor or Super Admin can manage Pit Tables."
            );
        }
    }

    public PitTable getTableById(UUID tableId) {
        PitTable table = repository.findById(tableId)
                .orElseThrow(() -> new RuntimeException("Pit table not found"));
        tableAccess.requireOperationalAccess(tableId);
        return table;
    }

    @Transactional
    public PitTable closeTable(UUID tableId, BigDecimal closingFloat) {

        businessDateService.validateSettlementMutationAllowed();
        businessDateService.validateBusinessDateIsOpen();

        if (systemLockService.isSystemLocked()) {
            throw new RuntimeException(
                    "System is locked. Pit table operations are not allowed."
            );
        }

        if (!currentUserRoleService.getCurrentRole()
                .map(rolePermissionService::canClosePitTable).orElse(false)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Only Pit Supervisor or Super Admin can close Pit Tables.");
        }

        PitTable table = repository.findByIdForUpdate(tableId)
                .orElseThrow(() -> new RuntimeException("Pit table not found"));

        if (!"OPEN".equalsIgnoreCase(table.getStatus())) {
            throw new ResourceConflictException("Pit table is already closed.");
        }
        if (!businessDateService.getCurrentBusinessDate().equals(table.getBusinessDate())) {
            throw new ResourceConflictException("Pit table does not belong to the current OPEN Business Date.");
        }
        if (closingFloat == null || closingFloat.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Closing float must be zero or greater.");
        }
        if (!assignmentRepository.findByPitTableIdAndStatusOrderByJoinedAtAsc(
                tableId, PitTableCustomerAssignmentStatus.ACTIVE).isEmpty()) {
            throw new ResourceConflictException(
                    "All active customer assignments must leave the Pit Table before it can be closed.");
        }
        chipCustodyService.validateTableCustodySettled(tableId);

        table.setStatus("CLOSED");
        table.setClosedAt(LocalDateTime.now());
        table.setClosingFloat(closingFloat);

        PitTable saved = repository.save(table);

        auditLogService.log(
                "CLOSE_PIT_TABLE",
                "PIT_TABLE",
                saved.getId(),
                currentUserRoleService.getCurrentUserId(),
                "Pit table closed: " + saved.getTableCode()
        );

        return saved;

    }

    public PitTable save(PitTable table) {

        businessDateService.validateNewOperationalMutationAllowed();
        businessDateService.validateBusinessDateIsOpen();

        if (systemLockService.isSystemLocked()) {
            throw new RuntimeException(
                    "System is locked. Pit table operations are not allowed."
            );
        }

        validatePitRole();

        table.setBusinessDate(businessDateService.getCurrentBusinessDate());
        table.setOpenedAt(java.time.LocalDateTime.now());
        table.setStatus("OPEN");

        PitTable saved = repository.save(table);

        auditLogService.log(
                "CREATE_PIT_TABLE",
                "PIT_TABLE",
                saved.getId(),
                currentUserRoleService.getCurrentUserId(),
                "Pit table created/opened: " + saved.getTableCode()
        );

        return saved;
    }

    public PitTable createAndOpen(CreatePitTableRequest request) {
        String tableCode = request.tableCode().trim().toUpperCase();
        repository.findByTableCodeIgnoreCase(tableCode).ifPresent(existing -> {
            throw new ResourceConflictException("A Pit Table with this table code already exists.");
        });

        PitTable table = new PitTable();
        table.setTableCode(tableCode);
        table.setTableName(request.tableName().trim());
        table.setGameType(request.gameType().trim());
        table.setOpeningFloat(request.openingFloat() == null ? BigDecimal.ZERO : request.openingFloat());
        table.setRemarks(request.remarks() == null ? null : request.remarks().trim());
        return save(table);
    }

    public List<PitTable> getOpenTables() {
        List<PitTable> openTables = repository.findByStatusIgnoreCase("OPEN");
        if (!tableAccess.isDealer()) {
            return openTables;
        }
        return tableAccess.currentDealerOperationId()
                .map(id -> openTables.stream().filter(table -> id.equals(table.getId())).toList())
                .orElseGet(List::of);
    }

    public List<PitTable> getAllTables() {
        return repository.findAll();
    }
}
