package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.VerifiedGamingResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VerifiedGamingResultRepository extends JpaRepository<VerifiedGamingResult, UUID> {
    List<VerifiedGamingResult> findByCustomerSessionIdOrderByCreatedAtAsc(UUID customerSessionId);
    Optional<VerifiedGamingResult> findByIdempotencyKey(String idempotencyKey);
    List<VerifiedGamingResult> findByCustomerIdAndBusinessDateOrderByCreatedAtAsc(UUID customerId, LocalDate businessDate);
    List<VerifiedGamingResult> findByBusinessDate(LocalDate businessDate);
    List<VerifiedGamingResult> findByPitTableId(UUID pitTableId);

    @Query(value = """
            select assignment_id as "assignmentId",
                   coalesce(sum(amount) filter (where result_type = 'WIN'), 0) as "verifiedWinTotal",
                   coalesce(sum(amount) filter (where result_type = 'LOSS'), 0) as "verifiedLossTotal"
              from casino.verified_gaming_results
             where pit_table_id = :tableId
             group by assignment_id
            """, nativeQuery = true)
    List<PitTablePlayerResultSummaryProjection> summarizeByTable(@Param("tableId") UUID tableId);
}
