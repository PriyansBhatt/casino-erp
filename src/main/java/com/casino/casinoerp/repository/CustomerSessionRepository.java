package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.CustomerSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Optional;

public interface CustomerSessionRepository extends JpaRepository<CustomerSession, UUID> {
    boolean existsByCustomerIdAndStatusIgnoreCase(UUID customerId, String status);

    Optional<CustomerSession> findFirstByCustomerIdAndStatusIgnoreCase(UUID customerId, String status);
}
