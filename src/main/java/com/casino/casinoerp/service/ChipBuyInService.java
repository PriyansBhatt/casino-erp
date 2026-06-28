package com.casino.casinoerp.service;

import com.casino.casinoerp.entity.ChipBuyIn;
import com.casino.casinoerp.entity.WalletTransaction;
import com.casino.casinoerp.repository.ChipBuyInRepository;
import com.casino.casinoerp.repository.CustomerWalletRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ChipBuyInService {

    private final ChipBuyInRepository repository;
    private final CustomerWalletRepository walletRepository;
    private final SystemLockService systemLockService;
    private final BusinessDateService businessDateService;
    private final WalletTransactionService walletTransactionService;
    private final AuditLogService auditLogService;
    private final RolePermissionService rolePermissionService;
    private final CurrentUserRoleService currentUserRoleService;

    public ChipBuyInService(
            ChipBuyInRepository repository,
            CustomerWalletRepository walletRepository,
            SystemLockService systemLockService,
            WalletTransactionService walletTransactionService,
            BusinessDateService businessDateService,
            AuditLogService auditLogService,
            RolePermissionService rolePermissionService,
            CurrentUserRoleService currentUserRoleService) {

        this.repository = repository;
        this.walletRepository = walletRepository;
        this.systemLockService = systemLockService;
        this.walletTransactionService = walletTransactionService;
        this.businessDateService = businessDateService;
        this.auditLogService = auditLogService;
        this.rolePermissionService = rolePermissionService;
        this.currentUserRoleService = currentUserRoleService;
    }

    public ChipBuyIn save(ChipBuyIn buyIn) {

        businessDateService.validateBusinessDateIsOpen();

        String role = currentUserRoleService.getCurrentUserRole();

        if (!rolePermissionService.canBuyIn(role)) {
            throw new RuntimeException(
                    "Access denied. Only Cashier or Super Admin can create buy-in."
            );
        }

        if (systemLockService.isSystemLocked()) {
            throw new RuntimeException(
                    "System is locked. Buy-In transactions are not allowed."
            );
        }

        buyIn.setBusinessDate(
                businessDateService.getCurrentBusinessDate()
        );

        WalletTransaction tx = new WalletTransaction();

        tx.setCustomerId(
                buyIn.getCustomerId()
        );

        tx.setCustomerSessionId(
                buyIn.getCustomerSessionId()
        );

        tx.setTransactionType("BUY_IN");

        tx.setAmount(
                buyIn.getTotalChipValueIssued()
        );

        tx.setRemarks(
                "Auto-created from Buy-In"
        );

        walletTransactionService.save(tx);

        ChipBuyIn saved = repository.save(buyIn);

        auditLogService.log(
                "CREATE_BUY_IN",
                "CHIP_BUY_IN",
                saved.getId(),
                currentUserRoleService.getCurrentUserId(),
                "Chip buy-in created: " + saved.getBuyInCode()
        );

        return saved;
    }

    public List<ChipBuyIn> getAllBuyIns() {
        return repository.findAll();
    }

    public List<ChipBuyIn> getBySessionId(UUID customerSessionId) {
        return repository.findByCustomerSessionId(customerSessionId);
    }
}