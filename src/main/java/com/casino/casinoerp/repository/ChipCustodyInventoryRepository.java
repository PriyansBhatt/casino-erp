package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.ChipCustodyInventory;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChipCustodyInventoryRepository extends JpaRepository<ChipCustodyInventory, java.util.UUID> {
    List<ChipCustodyInventory> findByLocationKeyOrderByDenomination(String locationKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("select inventory from ChipCustodyInventory inventory where inventory.locationKey = :locationKey and inventory.denomination = :denomination")
    Optional<ChipCustodyInventory> findForUpdate(
            @Param("locationKey") String locationKey,
            @Param("denomination") Integer denomination);
}
