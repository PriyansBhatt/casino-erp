package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.PitTableTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PitTableTransactionRepository extends JpaRepository<PitTableTransaction, UUID> {
    List<PitTableTransaction> findByPitTableId(UUID pitTableId);
}