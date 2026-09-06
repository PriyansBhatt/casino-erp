package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.LegacyPitTableReconciliationResolution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LegacyPitTableReconciliationResolutionRepository
        extends JpaRepository<LegacyPitTableReconciliationResolution, UUID> {
    Optional<LegacyPitTableReconciliationResolution> findByPitTableId(UUID pitTableId);
    Optional<LegacyPitTableReconciliationResolution> findByIdempotencyKey(String idempotencyKey);
}
