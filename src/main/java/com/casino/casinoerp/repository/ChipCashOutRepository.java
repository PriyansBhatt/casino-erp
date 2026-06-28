package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.ChipCashOut;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChipCashOutRepository extends JpaRepository<ChipCashOut, UUID> {
    List<ChipCashOut> findByCustomerSessionId(UUID customerSessionId);
}