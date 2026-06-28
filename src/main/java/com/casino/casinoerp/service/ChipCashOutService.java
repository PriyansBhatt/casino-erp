package com.casino.casinoerp.service;

import com.casino.casinoerp.entity.ChipCashOut;
import com.casino.casinoerp.entity.WalletTransaction;
import com.casino.casinoerp.repository.ChipCashOutRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ChipCashOutService {

    private final ChipCashOutRepository repository;
    private final SystemLockService systemLockService;
    private final BusinessDateService businessDateService;
    private final WalletTransactionService walletTransactionService;
    private final AuditLogService auditLogService;
    private final RolePermissionService rolePermissionService;
    private final CurrentUserRoleService currentUserRoleService;

    public ChipCashOutService(
            ChipCashOutRepository repository,
            SystemLockService systemLockService,
            BusinessDateService businessDateService,
            WalletTransactionService walletTransactionService,
            AuditLogService auditLogService,
            RolePermissionService rolePermissionService,
            CurrentUserRoleService currentUserRoleService) {

        this.repository = repository;
        this.systemLockService = systemLockService;
        this.businessDateService = businessDateService;
        this.walletTransactionService = walletTransactionService;
        this.auditLogService = auditLogService;
        this.rolePermissionService = rolePermissionService;
        this.currentUserRoleService = currentUserRoleService;
    }

    @Transactional
    public ChipCashOut save(ChipCashOut cashOut) {

        businessDateService.validateBusinessDateIsOpen();

        String role = currentUserRoleService.getCurrentUserRole();

        if (!rolePermissionService.canCashOut(role)) {
            throw new RuntimeException(
                    "Access denied. Only Cashier or Super Admin can perform cash-out."
            );
        }

        if (systemLockService.isSystemLocked()) {
            throw new RuntimeException(
                    "System is locked. Cash-Out transactions are not allowed.");
        }

        cashOut.setBusinessDate(
                businessDateService.getCurrentBusinessDate()
        );

        ChipCashOut savedCashOut = repository.save(cashOut);

        WalletTransaction tx = new WalletTransaction();
        tx.setCustomerId(savedCashOut.getCustomerId());
        tx.setCustomerSessionId(savedCashOut.getCustomerSessionId());
        tx.setTransactionType("CASH_OUT");
        tx.setAmount(savedCashOut.getCashPaid());
        tx.setRemarks("Auto-created from Cash-Out");

        walletTransactionService.save(tx);

        auditLogService.log(
                "CREATE_CASH_OUT",
                "CHIP_CASH_OUT",
                savedCashOut.getId(),
                currentUserRoleService.getCurrentUserId(),
                "Chip cash-out created: " + savedCashOut.getCashOutCode()
        );

        return savedCashOut;

    }



    public List<ChipCashOut> getBySessionId(UUID customerSessionId) {
        return repository.findByCustomerSessionId(customerSessionId);
    }

    public List<ChipCashOut> getAllCashOuts() {
        return repository.findAll();
    }
}