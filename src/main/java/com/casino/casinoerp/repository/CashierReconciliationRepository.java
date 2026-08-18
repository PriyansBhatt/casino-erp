package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.CashierReconciliation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface CashierReconciliationRepository extends JpaRepository<CashierReconciliation, UUID> {
    Optional<CashierReconciliation> findByCashierUserIdAndBusinessDate(UUID cashierUserId, LocalDate businessDate);
    Optional<CashierReconciliation> findByIdempotencyKey(String idempotencyKey);
    List<CashierReconciliation> findByBusinessDateOrderBySubmittedAtDesc(LocalDate businessDate);
}
