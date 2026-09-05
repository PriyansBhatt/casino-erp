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

public interface PitTableCustomerAssignmentRepository extends JpaRepository<PitTableCustomerAssignment, UUID> {
    List<PitTableCustomerAssignment> findByPitTableIdAndStatusOrderByJoinedAtAsc(
            UUID pitTableId, PitTableCustomerAssignmentStatus status);
    List<PitTableCustomerAssignment> findByPitTableIdOrderByJoinedAtAsc(UUID pitTableId);
    Optional<PitTableCustomerAssignment> findByCustomerSessionIdAndStatus(
            UUID customerSessionId, PitTableCustomerAssignmentStatus status);

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
}
