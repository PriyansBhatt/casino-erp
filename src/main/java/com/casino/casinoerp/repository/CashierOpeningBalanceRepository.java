package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.CashierOpeningBalance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface CashierOpeningBalanceRepository extends JpaRepository<CashierOpeningBalance, UUID> {
    Optional<CashierOpeningBalance> findByCashierUserIdAndBusinessDate(UUID cashierUserId, LocalDate businessDate);
}
