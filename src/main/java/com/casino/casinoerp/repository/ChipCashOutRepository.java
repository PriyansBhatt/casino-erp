package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.ChipCashOut;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDate;

public interface ChipCashOutRepository extends JpaRepository<ChipCashOut, UUID> {
    @org.springframework.data.jpa.repository.Query("select distinct c from ChipCashOut c left join fetch c.denominations where c.customerSessionId = :sessionId order by c.createdAt desc, c.id desc")
    List<ChipCashOut> findHistoryBySession(@org.springframework.data.repository.query.Param("sessionId") UUID sessionId);

    @org.springframework.data.jpa.repository.Query("select distinct c from ChipCashOut c left join fetch c.denominations order by c.createdAt desc, c.id desc")
    List<ChipCashOut> findHistory();

    List<ChipCashOut> findByCustomerSessionId(UUID customerSessionId);
    Optional<ChipCashOut> findByIdempotencyKey(String idempotencyKey);
    List<ChipCashOut> findByBusinessDateAndCreatedBy(LocalDate businessDate, UUID createdBy);
    List<ChipCashOut> findByCustomerIdAndBusinessDate(UUID customerId, LocalDate businessDate);
    List<ChipCashOut> findByBusinessDate(LocalDate businessDate);
}
