package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.*;

public interface StaffRosterAssignmentRepository extends JpaRepository<StaffRosterAssignment,UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select roster from StaffRosterAssignment roster where roster.id = :id")
    Optional<StaffRosterAssignment> findByIdForUpdate(@Param("id") UUID id);

    List<StaffRosterAssignment> findByStaffProfileIdAndStatusAndRosterDateBetweenOrderByRosterDateAsc(
            UUID staffProfileId,RosterStatus status,LocalDate from,LocalDate to);

    @Query("""
            select roster from StaffRosterAssignment roster
            where (:fromDate is null or roster.rosterDate >= :fromDate)
              and (:toDate is null or roster.rosterDate <= :toDate)
              and (:staffProfileId is null or roster.staffProfileId = :staffProfileId)
              and (:shiftId is null or roster.shiftDefinitionId = :shiftId)
              and (:status is null or roster.status = :status)
              and (:departmentId is null or roster.staffProfileId in
                  (select staff.id from StaffProfile staff where staff.departmentId = :departmentId))
            order by roster.rosterDate asc, roster.createdAt asc
            """)
    List<StaffRosterAssignment> search(@Param("fromDate") LocalDate fromDate,@Param("toDate") LocalDate toDate,
            @Param("staffProfileId") UUID staffProfileId,@Param("departmentId") UUID departmentId,
            @Param("shiftId") UUID shiftId,@Param("status") RosterStatus status);
}
