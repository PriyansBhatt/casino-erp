package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.PitTableCustomerAssignment;
import com.casino.casinoerp.entity.PitTableCustomerAssignmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDate;

public interface PitTableCustomerAssignmentRepository extends JpaRepository<PitTableCustomerAssignment, UUID> {
    List<PitTableCustomerAssignment> findByPitTableIdAndStatusOrderByJoinedAtAsc(
            UUID pitTableId, PitTableCustomerAssignmentStatus status);
    List<PitTableCustomerAssignment> findByPitTableIdOrderByJoinedAtAsc(UUID pitTableId);
    Optional<PitTableCustomerAssignment> findByCustomerSessionIdAndStatus(
            UUID customerSessionId, PitTableCustomerAssignmentStatus status);
    List<PitTableCustomerAssignment> findByBusinessDateAndStatusOrderByJoinedAtAsc(
            LocalDate businessDate, PitTableCustomerAssignmentStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select assignment from PitTableCustomerAssignment assignment " +
            "where assignment.id = :assignmentId")
    Optional<PitTableCustomerAssignment> findByIdForUpdate(@Param("assignmentId") UUID assignmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select assignment from PitTableCustomerAssignment assignment " +
            "where assignment.customerSessionId = :sessionId and assignment.pitTableId = :tableId " +
            "and assignment.status = com.casino.casinoerp.entity.PitTableCustomerAssignmentStatus.ACTIVE")
    Optional<PitTableCustomerAssignment> findActiveForUpdate(
            @Param("sessionId") UUID sessionId, @Param("tableId") UUID tableId);

    @Query(value = """
            select a.id as "assignmentId", c.id as "customerId",
                   c.customer_code as "customerCode", c.full_name as "customerName",
                   s.id as "customerSessionId", s.session_code as "sessionCode",
                   a.joined_at as "joinedAt"
              from casino.pit_table_customer_assignments a
              join customer.customers c on c.id = a.customer_id
              join session.customer_sessions s on s.id = a.customer_session_id
             where a.pit_table_id = :tableId and a.status = 'ACTIVE'
             order by a.joined_at
            """, nativeQuery = true)
    List<PitTableModePlayerProjection> findActiveModePlayers(@Param("tableId") UUID tableId);
}
