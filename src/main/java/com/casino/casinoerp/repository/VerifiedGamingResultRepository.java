package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.VerifiedGamingResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDate;

public interface VerifiedGamingResultRepository extends JpaRepository<VerifiedGamingResult, UUID> {
    List<VerifiedGamingResult> findByCustomerSessionIdOrderByCreatedAtAsc(UUID customerSessionId);
    Optional<VerifiedGamingResult> findByIdempotencyKey(String idempotencyKey);
    List<VerifiedGamingResult> findByCustomerIdAndBusinessDateOrderByCreatedAtAsc(UUID customerId, LocalDate businessDate);
    List<VerifiedGamingResult> findByBusinessDate(LocalDate businessDate);
    List<VerifiedGamingResult> findByPitTableId(UUID pitTableId);
}
