package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.*;

public interface StaffLeaveRequestRepository extends JpaRepository<StaffLeaveRequest, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from StaffLeaveRequest request where request.id = :id")
    Optional<StaffLeaveRequest> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select request from StaffLeaveRequest request
            where request.staffProfileId = :staffProfileId
              and request.status in :statuses
              and request.startDate <= :endDate
              and request.endDate >= :startDate
            """)
    List<StaffLeaveRequest> findOverlapping(
            @Param("staffProfileId") UUID staffProfileId,
            @Param("statuses") Collection<LeaveRequestStatus> statuses,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("""
            select request from StaffLeaveRequest request
            where request.staffProfileId = :staffProfileId
              and request.status = :status
              and request.startDate <= :endDate
              and request.endDate >= :startDate
            order by request.startDate asc
            """)
    List<StaffLeaveRequest> findByStaffAndStatusOverlappingDates(
            @Param("staffProfileId") UUID staffProfileId,
            @Param("status") LeaveRequestStatus status,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("""
            select request from StaffLeaveRequest request
            where (:staffProfileId is null or request.staffProfileId = :staffProfileId)
              and (:departmentId is null or request.staffProfileId in
                  (select staff.id from StaffProfile staff where staff.departmentId = :departmentId))
              and (:leaveTypeId is null or request.leaveTypeId = :leaveTypeId)
              and (:status is null or request.status = :status)
              and (:startDate is null or request.endDate >= :startDate)
              and (:endDate is null or request.startDate <= :endDate)
            order by request.startDate desc, request.submittedAt desc
            """)
    List<StaffLeaveRequest> search(
            @Param("staffProfileId") UUID staffProfileId,
            @Param("departmentId") UUID departmentId,
            @Param("leaveTypeId") UUID leaveTypeId,
            @Param("status") LeaveRequestStatus status,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
