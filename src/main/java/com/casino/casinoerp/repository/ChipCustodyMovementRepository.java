package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.ChipCustodyMovement;
import com.casino.casinoerp.entity.ChipCustodyMovementType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChipCustodyMovementRepository extends JpaRepository<ChipCustodyMovement, UUID> {
    Optional<ChipCustodyMovement> findByIdempotencyKey(String idempotencyKey);
    boolean existsByMovementType(ChipCustodyMovementType movementType);
    boolean existsByMovementTypeAndPitTableId(ChipCustodyMovementType movementType, UUID pitTableId);
    List<ChipCustodyMovement> findByBusinessDateOrderByCreatedAtDesc(LocalDate businessDate);
}
