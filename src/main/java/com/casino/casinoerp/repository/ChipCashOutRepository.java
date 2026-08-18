package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.ChipCashOut;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDate;

public interface ChipCashOutRepository extends JpaRepository<ChipCashOut, UUID> {
    List<ChipCashOut> findByCustomerSessionId(UUID customerSessionId);
    Optional<ChipCashOut> findByIdempotencyKey(String idempotencyKey);
    List<ChipCashOut> findByBusinessDateAndCreatedBy(LocalDate businessDate, UUID createdBy);
    List<ChipCashOut> findByCustomerIdAndBusinessDate(UUID customerId, LocalDate businessDate);
    List<ChipCashOut> findByBusinessDate(LocalDate businessDate);
}
