package com.casino.casinoerp.service;

import com.casino.casinoerp.entity.WalletTransaction;
import com.casino.casinoerp.repository.WalletTransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class WalletTransactionService {

    private final WalletTransactionRepository repository;
    private final BusinessDateService businessDateService;
    private final SystemLockService systemLockService;

    public WalletTransactionService(
            WalletTransactionRepository repository,
            BusinessDateService businessDateService,
            SystemLockService systemLockService) {

        this.repository = repository;
        this.businessDateService = businessDateService;
        this.systemLockService = systemLockService;
    }

    public BigDecimal getSessionBalance(UUID customerId, UUID sessionId) {

        return repository.findByCustomerIdAndCustomerSessionId(customerId, sessionId)
                .stream()
                .map(tx -> {
                    if ("BUY_IN".equalsIgnoreCase(tx.getTransactionType())) {
                        return tx.getAmount();
                    } else if ("CASH_OUT".equalsIgnoreCase(tx.getTransactionType())) {
                        return tx.getAmount().negate();
                    }
                    return BigDecimal.ZERO;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getCustomerBalance(UUID customerId) {

        return repository.findByCustomerId(customerId)
                .stream()
                .map(tx -> {
                    if ("BUY_IN".equalsIgnoreCase(tx.getTransactionType())) {
                        return tx.getAmount();
                    } else if ("CASH_OUT".equalsIgnoreCase(tx.getTransactionType())) {
                        return tx.getAmount().negate();
                    }
                    return BigDecimal.ZERO;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<WalletTransaction> getByBusinessDate(LocalDate businessDate) {
        return repository.findByBusinessDate(businessDate);
    }

    public WalletTransaction save(WalletTransaction transaction) {

        businessDateService.validateBusinessDateIsOpen();

        if (systemLockService.isSystemLocked()) {
            throw new RuntimeException(
                    "System is locked. Wallet transactions are not allowed."
            );
        }

        transaction.setBusinessDate(
                businessDateService.getCurrentBusinessDate()
        );

        transaction.setCreatedAt(
                LocalDateTime.now()
        );

        return repository.save(transaction);
    }

    public List<WalletTransaction> getByCustomer(UUID customerId) {
        return repository.findByCustomerId(customerId);
    }

    public List<WalletTransaction> getBySession(UUID sessionId) {
        return repository.findByCustomerSessionId(sessionId);
    }

    public List<WalletTransaction> getAll() {
        return repository.findAll();
    }
}