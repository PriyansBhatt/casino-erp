package com.casino.casinoerp.service;

import com.casino.casinoerp.entity.PitTableTransaction;
import com.casino.casinoerp.repository.PitTableTransactionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PitTableTransactionService {

    private final PitTableTransactionRepository repository;
    private final BusinessDateService businessDateService;
    private final SystemLockService systemLockService;
    private final AuditLogService auditLogService;
    private final RolePermissionService rolePermissionService;
    private final CurrentUserRoleService currentUserRoleService;

    public PitTableTransactionService(PitTableTransactionRepository repository,
                                      BusinessDateService businessDateService,
                                      SystemLockService systemLockService,
                                      AuditLogService auditLogService,
                                      RolePermissionService rolePermissionService,
                                      CurrentUserRoleService currentUserRoleService){

        this.repository = repository;
        this.businessDateService = businessDateService;
        this.systemLockService = systemLockService;
        this.auditLogService = auditLogService;
        this.rolePermissionService = rolePermissionService;
        this.currentUserRoleService = currentUserRoleService;
    }

    public PitTableTransaction save(PitTableTransaction transaction) {

        businessDateService.validateNewOperationalMutationAllowed();
        businessDateService.validateBusinessDateIsOpen();
        
        String role = currentUserRoleService.getCurrentUserRole();

        if (!rolePermissionService.canPitTransaction(role)) {
            throw new RuntimeException(
                    "Access denied. Only Dealer, Pit Supervisor or Super Admin can create pit transactions."
            );
        }

        if (systemLockService.isSystemLocked()) {
            throw new RuntimeException(
                    "System is locked. Pit table transactions are not allowed."
            );
        }

        transaction.setBusinessDate(
                businessDateService.getCurrentBusinessDate()
        );

        transaction.setCreatedAt(LocalDateTime.now());

        PitTableTransaction saved = repository.save(transaction);

        auditLogService.log(
                "CREATE_PIT_TRANSACTION",
                "PIT_TABLE",
                saved.getId(),
                currentUserRoleService.getCurrentUserId(),
                "Pit table transaction created"
        );

        return saved;
    }

    public List<PitTableTransaction> getTransactionsByTable(UUID pitTableId) {
        return repository.findByPitTableId(pitTableId);
    }

    public List<PitTableTransaction> getAllTransactions() {
        return repository.findAll();
    }

    public List<PitTableTransaction> getByTable(UUID pitTableId) {
        return repository.findAll()
                .stream()
                .filter(t -> pitTableId.equals(t.getPitTableId()))
                .toList();
    }
}
