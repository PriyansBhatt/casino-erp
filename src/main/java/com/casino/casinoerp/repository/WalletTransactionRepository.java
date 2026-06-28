package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID> {

    List<WalletTransaction> findByCustomerId(UUID customerId);

    List<WalletTransaction> findByCustomerSessionId(UUID customerSessionId);

    List<WalletTransaction> findByCustomerIdAndCustomerSessionId(
            UUID customerId,
            UUID customerSessionId
    );

    List<WalletTransaction> findByBusinessDate(LocalDate businessDate);
}