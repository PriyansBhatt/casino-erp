package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.StaffAttendance;
import com.casino.casinoerp.entity.StaffAttendanceStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StaffAttendanceRepository extends JpaRepository<StaffAttendance, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select attendance from StaffAttendance attendance where attendance.id = :id")
    Optional<StaffAttendance> findByIdForUpdate(@Param("id") UUID id);

    Optional<StaffAttendance> findFirstByUserIdAndStatusOrderByCheckInAtDesc(
            UUID userId, StaffAttendanceStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select attendance from StaffAttendance attendance
            where attendance.user.id = :userId and attendance.status = :status
            order by attendance.checkInAt desc
            """)
    Optional<StaffAttendance> findOpenByUserIdForUpdate(
            @Param("userId") UUID userId,
            @Param("status") StaffAttendanceStatus status);

    List<StaffAttendance> findByUserIdOrderByCheckInAtDesc(UUID userId);

    List<StaffAttendance> findByUserIdAndBusinessDateOrderByCheckInAtDesc(
            UUID userId, LocalDate businessDate);

    List<StaffAttendance> findByBusinessDateOrderByCheckInAtAsc(LocalDate businessDate);
}
