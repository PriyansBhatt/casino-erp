package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.ChipBuyIn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.List;
import java.util.Optional;
import java.time.LocalDate;


public interface ChipBuyInRepository extends JpaRepository<ChipBuyIn, UUID> {
    List<ChipBuyIn> findByCustomerSessionId(UUID customerSessionId);
    Optional<ChipBuyIn> findByIdempotencyKey(String idempotencyKey);
    List<ChipBuyIn> findByBusinessDateAndCreatedBy(LocalDate businessDate, UUID createdBy);
    List<ChipBuyIn> findByCustomerIdAndBusinessDate(UUID customerId, LocalDate businessDate);
    List<ChipBuyIn> findByBusinessDateOrderByCreatedAtDesc(LocalDate businessDate);
    List<ChipBuyIn> findByBusinessDate(LocalDate businessDate);
}
