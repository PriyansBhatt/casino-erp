package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.CustomerBonus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.*;

public interface CustomerBonusRepository extends JpaRepository<CustomerBonus, UUID> {
    Optional<CustomerBonus> findByIdempotencyKey(String idempotencyKey);
    List<CustomerBonus> findByBusinessDateOrderByCreatedAtDesc(LocalDate businessDate);
    List<CustomerBonus> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);
}
