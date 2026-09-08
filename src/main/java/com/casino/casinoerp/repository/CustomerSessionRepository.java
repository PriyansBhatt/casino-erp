package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.CustomerSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import java.time.LocalDate;

public interface CustomerSessionRepository extends JpaRepository<CustomerSession, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("select session from CustomerSession session where session.id = :sessionId")
    Optional<CustomerSession> findByIdForUpdate(@Param("sessionId") UUID sessionId);

    boolean existsByCustomerIdAndStatusIgnoreCase(UUID customerId, String status);

    Optional<CustomerSession> findFirstByCustomerIdAndStatusIgnoreCase(UUID customerId, String status);

    List<CustomerSession> findByStatusIgnoreCaseAndBusinessDateOrderByEntryTimeAsc(
            String status, java.time.LocalDate businessDate);

    List<CustomerSession> findByBusinessDateOrderByEntryTimeAsc(LocalDate businessDate);

    @Query(value = """
            select
                cs.customer_id as "customerId",
                count(*) as "totalVisits",
                (array_agg(cs.business_date order by cs.entry_time desc nulls last,
                    cs.created_at desc nulls last, cs.id desc))[1] as "lastVisitBusinessDate",
                (array_agg(cs.entry_time order by cs.entry_time desc nulls last,
                    cs.created_at desc nulls last, cs.id desc))[1] as "lastEntryTime",
                bool_or(upper(cs.status) = 'OPEN') as "hasActiveSession",
                (array_agg(cs.id order by cs.entry_time desc nulls last,
                    cs.created_at desc nulls last, cs.id desc)
                    filter (where upper(cs.status) = 'OPEN'))[1] as "activeSessionId"
            from session.customer_sessions cs
            where cs.customer_id in (:customerIds)
            group by cs.customer_id
            """, nativeQuery = true)
    List<CustomerVisitSummaryProjection> findVisitSummariesByCustomerIds(
            @Param("customerIds") Collection<UUID> customerIds
    );
}
