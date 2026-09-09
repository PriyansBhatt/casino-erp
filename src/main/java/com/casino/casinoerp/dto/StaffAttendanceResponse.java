package com.casino.casinoerp.dto;

import com.casino.casinoerp.entity.StaffAttendanceStatus;
import com.casino.casinoerp.entity.AttendanceScheduleStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record StaffAttendanceResponse(
        UUID attendanceId,
        AttendanceEmployeeResponse employee,
        LocalDate businessDate,
        StaffAttendanceStatus status,
        Instant checkInAt,
        Instant checkOutAt,
        Long workedMinutes,
        UUID rosterAssignmentId,
        boolean scheduled,
        String shiftCode,
        String shiftName,
        LocalDate rosterDate,
        Instant scheduledStartAt,
        Instant scheduledEndAt,
        AttendanceScheduleStatus attendanceScheduleStatus,
        long lateByMinutes,
        Long earlyDepartureMinutes,
        Instant createdAt,
        Instant updatedAt
) {}
