package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.LegacyCashActorResolution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LegacyCashActorResolutionRepository
        extends JpaRepository<LegacyCashActorResolution, UUID> {
    Optional<LegacyCashActorResolution> findByActorUserIdAndBusinessDate(
            UUID actorUserId, LocalDate businessDate);
    Optional<LegacyCashActorResolution> findByIdempotencyKey(String idempotencyKey);
    List<LegacyCashActorResolution> findByBusinessDateOrderByResolvedAtDesc(LocalDate businessDate);
}
