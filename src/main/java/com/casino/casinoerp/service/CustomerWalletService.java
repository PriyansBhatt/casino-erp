package com.casino.casinoerp.service;

import com.casino.casinoerp.entity.CustomerWallet;
import com.casino.casinoerp.repository.CustomerWalletRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CustomerWalletService {

    private final CustomerWalletRepository repository;
    private final BusinessDateService businessDateService;
    private final SystemLockService systemLockService;
    private final AuditLogService auditLogService;
    private final CurrentUserRoleService currentUserRoleService;

    public CustomerWalletService(
            CustomerWalletRepository repository,
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

    public List<CustomerWallet> getAllWallets() {
        return repository.findAll();
    }

    public Optional<CustomerWallet> getWalletByCustomerId(UUID customerId) {
        return repository.findByCustomerId(customerId);
    }

    public CustomerWallet save(CustomerWallet wallet) {

        businessDateService.validateNewOperationalMutationAllowed();
        businessDateService.validateBusinessDateIsOpen();

        String role = currentUserRoleService.getCurrentUserRole();

        if (!role.equalsIgnoreCase("Cashier")
                && !role.equalsIgnoreCase("Manager")
                && !role.equalsIgnoreCase("Super Admin")
                && !role.equalsIgnoreCase("SUPER_ADMIN")) {

            throw new RuntimeException(
                    "Access denied. Only Cashier, Manager or Super Admin can create/update wallet."
            );
        }

        if (systemLockService.isSystemLocked()) {
            throw new RuntimeException(
                    "System is locked. Wallet transactions are not allowed."
            );
        }

        if (wallet.getId() == null) {
            wallet.setId(UUID.randomUUID());
        }

        wallet.setUpdatedAt(LocalDateTime.now());

        CustomerWallet saved = repository.save(wallet);

        auditLogService.log(
                "CREATE_WALLET",
                "CUSTOMER_WALLET",
                saved.getId(),
                currentUserRoleService.getCurrentUserId(),
                "Customer wallet created/updated"
        );

        return saved;
    }
}
