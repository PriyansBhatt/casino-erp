package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.PhysicalPitTable;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PhysicalPitTableRepository extends JpaRepository<PhysicalPitTable, UUID> {
    List<PhysicalPitTable> findAllByOrderByTableCodeAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("select value from PhysicalPitTable value where value.id = :id")
    Optional<PhysicalPitTable> findByIdForUpdate(@Param("id") UUID id);
}
