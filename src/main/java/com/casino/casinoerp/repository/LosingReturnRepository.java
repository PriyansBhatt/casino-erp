package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.LosingReturn;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.*;

public interface LosingReturnRepository extends JpaRepository<LosingReturn, UUID> {
    Optional<LosingReturn> findByIdempotencyKey(String key);
    List<LosingReturn> findByCustomerIdAndBusinessDateOrderByCreatedAtAsc(UUID customerId, LocalDate businessDate);
    List<LosingReturn> findByBusinessDateAndCreatedBy(LocalDate businessDate, UUID createdBy);
    List<LosingReturn> findByBusinessDate(LocalDate businessDate);
}
