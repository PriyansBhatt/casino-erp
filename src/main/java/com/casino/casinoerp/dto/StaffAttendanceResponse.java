package com.casino.casinoerp.dto;

import com.casino.casinoerp.entity.StaffAttendanceStatus;

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
        Instant createdAt,
        Instant updatedAt
) {}
