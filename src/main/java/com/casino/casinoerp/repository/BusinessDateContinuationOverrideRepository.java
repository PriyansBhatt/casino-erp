package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.BusinessDateContinuationOverride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface BusinessDateContinuationOverrideRepository
        extends JpaRepository<BusinessDateContinuationOverride, UUID> {
    Optional<BusinessDateContinuationOverride> findByBusinessDateAndRevokedAtIsNull(LocalDate businessDate);
}
