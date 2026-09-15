package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.PitTableStaffAssignment;
import com.casino.casinoerp.entity.PitTableStaffAssignmentRole;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDate;

public interface PitTableStaffAssignmentRepository extends JpaRepository<PitTableStaffAssignment, UUID> {
    boolean existsByPitTableIdAndEndedAtIsNull(UUID pitTableId);
    List<PitTableStaffAssignment> findByPitTableIdAndEndedAtIsNullOrderByAssignmentRoleAsc(UUID pitTableId);
    List<PitTableStaffAssignment> findByPitTableIdOrderByStartedAtAsc(UUID pitTableId);
    Optional<PitTableStaffAssignment> findByPitTableIdAndAssignmentRoleAndEndedAtIsNull(
            UUID pitTableId, PitTableStaffAssignmentRole assignmentRole);
    List<PitTableStaffAssignment> findByStaffUserIdAndEndedAtIsNull(UUID staffUserId);
    List<PitTableStaffAssignment> findByBusinessDateAndEndedAtIsNullOrderByStartedAtAsc(
            LocalDate businessDate);
    Optional<PitTableStaffAssignment> findByStaffUserIdAndAssignmentRoleAndEndedAtIsNull(
            UUID staffUserId, PitTableStaffAssignmentRole assignmentRole);
    Optional<PitTableStaffAssignment> findByAssignmentIdempotencyKey(String key);
    Optional<PitTableStaffAssignment> findByEndIdempotencyKey(String key);
    boolean existsByPitTableIdAndStaffUserIdAndAssignmentRoleAndEndedAtIsNull(
            UUID pitTableId, UUID staffUserId, PitTableStaffAssignmentRole assignmentRole);

    @Query("select assignment.pitTableId as pitTableId, assignment.id as assignmentId, "
            + "user.id as userId, user.username as username, user.fullName as displayName, "
            + "assignment.assignmentRole as assignmentRole, assignment.startedAt as startedAt "
            + "from PitTableStaffAssignment assignment, User user "
            + "where assignment.staffUserId = user.id "
            + "and assignment.pitTableId in :tableIds and assignment.endedAt is null "
            + "order by assignment.pitTableId, assignment.assignmentRole, assignment.startedAt")
    List<PitTableActiveStaffProjection> findActiveStaffForOverview(
            @Param("tableIds") List<UUID> tableIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("select assignment from PitTableStaffAssignment assignment where assignment.id = :assignmentId")
    Optional<PitTableStaffAssignment> findByIdForUpdate(@Param("assignmentId") UUID assignmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("select assignment from PitTableStaffAssignment assignment "
            + "where assignment.pitTableId = :tableId and assignment.endedAt is null")
    List<PitTableStaffAssignment> findActiveByPitTableIdForUpdate(@Param("tableId") UUID tableId);
}
