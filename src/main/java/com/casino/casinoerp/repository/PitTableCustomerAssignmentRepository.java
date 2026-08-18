package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.PitTableCustomerAssignment;
import com.casino.casinoerp.entity.PitTableCustomerAssignmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PitTableCustomerAssignmentRepository extends JpaRepository<PitTableCustomerAssignment, UUID> {
    List<PitTableCustomerAssignment> findByPitTableIdAndStatusOrderByJoinedAtAsc(
            UUID pitTableId, PitTableCustomerAssignmentStatus status);
    List<PitTableCustomerAssignment> findByPitTableIdOrderByJoinedAtAsc(UUID pitTableId);
    Optional<PitTableCustomerAssignment> findByCustomerSessionIdAndStatus(
            UUID customerSessionId, PitTableCustomerAssignmentStatus status);
}
