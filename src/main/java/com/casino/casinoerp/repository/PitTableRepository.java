package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.PitTable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

public interface PitTableRepository extends JpaRepository<PitTable, UUID> {
    List<PitTable> findByStatusIgnoreCase(String status);
    Optional<PitTable> findByTableCodeIgnoreCase(String tableCode);
}
