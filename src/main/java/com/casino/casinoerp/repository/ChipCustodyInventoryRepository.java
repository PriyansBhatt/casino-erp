package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.ChipCustodyInventory;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Collection;
import java.util.UUID;

public interface ChipCustodyInventoryRepository extends JpaRepository<ChipCustodyInventory, java.util.UUID> {
    List<ChipCustodyInventory> findByLocationKeyOrderByDenomination(String locationKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("select inventory from ChipCustodyInventory inventory where inventory.locationKey = :locationKey and inventory.denomination = :denomination")
    Optional<ChipCustodyInventory> findForUpdate(
            @Param("locationKey") String locationKey,
            @Param("denomination") Integer denomination);

    @Query(value = """
            select reference_id as "customerSessionId",
                   cast(coalesce(sum(denomination * quantity), 0) as numeric) as "custodyTotal"
              from cashier.chip_custody_inventory
             where location_type = 'CUSTOMER_SESSION' and reference_id in (:sessionIds)
             group by reference_id
            """, nativeQuery = true)
    List<CustomerSessionCustodySummaryProjection> summarizeCustomerSessions(
            @Param("sessionIds") Collection<UUID> sessionIds);
}
