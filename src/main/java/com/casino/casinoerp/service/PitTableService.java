package com.casino.casinoerp.service;

import com.casino.casinoerp.entity.PitTable;
import com.casino.casinoerp.repository.PitTableRepository;
import org.springframework.stereotype.Service;

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

    public PitTableService(
            PitTableRepository repository,
            BusinessDateService businessDateService,
            SystemLockService systemLockService,
            AuditLogService auditLogService,
            CurrentUserRoleService currentUserRoleService) {

        this.repository = repository;
        this.businessDateService = businessDateService;
        this.systemLockService = systemLockService;
        this.auditLogService = auditLogService;
        this.currentUserRoleService = currentUserRoleService;
    }

    private void validatePitRole() {

        String role = currentUserRoleService.getCurrentUserRole();

        if (!(role.equalsIgnoreCase("SUPER_ADMIN")
                || role.equalsIgnoreCase("Super Admin")
                || role.equalsIgnoreCase("MANAGER")
                || role.equalsIgnoreCase("Manager")
                || role.equalsIgnoreCase("PIT_SUPERVISOR")
                || role.equalsIgnoreCase("Pit Supervisor")
                || role.equalsIgnoreCase("DEALER")
                || role.equalsIgnoreCase("Dealer"))) {

            throw new RuntimeException(
                    "Only Dealer, Pit Supervisor, Manager or Super Admin can manage Pit Tables."
            );
        }
    }

    public PitTable getTableById(UUID tableId) {
        return repository.findById(tableId)
                .orElseThrow(() -> new RuntimeException("Pit table not found"));
    }

    public PitTable closeTable(UUID tableId, BigDecimal closingFloat) {

        businessDateService.validateBusinessDateIsOpen();

        if (systemLockService.isSystemLocked()) {
            throw new RuntimeException(
                    "System is locked. Pit table operations are not allowed."
            );
        }

        validatePitRole();

        PitTable table = repository.findById(tableId)
                .orElseThrow(() -> new RuntimeException("Pit table not found"));

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

    public List<PitTable> getOpenTables() {
        return repository.findByStatusIgnoreCase("OPEN");
    }

    public List<PitTable> getAllTables() {
        return repository.findAll();
    }
}