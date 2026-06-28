package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.ChipBuyIn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.List;


public interface ChipBuyInRepository extends JpaRepository<ChipBuyIn, UUID> {
    List<ChipBuyIn> findByCustomerSessionId(UUID customerSessionId);
}