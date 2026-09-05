package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.PitTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

public interface PitTableRepository extends JpaRepository<PitTable, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("select table from PitTable table where table.id = :tableId")
    Optional<PitTable> findByIdForUpdate(@Param("tableId") UUID tableId);

    List<PitTable> findByStatusIgnoreCase(String status);
    Optional<PitTable> findByTableCodeIgnoreCase(String tableCode);
}
