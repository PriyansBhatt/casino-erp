package com.casino.casinoerp.dto;

import com.casino.casinoerp.entity.StaffAttendanceCorrectionType;

import java.time.Instant;
import java.util.UUID;

public record StaffAttendanceCorrectionResponse(
        UUID correctionId,
        UUID attendanceId,
        StaffAttendanceCorrectionType correctionType,
        Instant previousCheckInAt,
        Instant newCheckInAt,
        Instant previousCheckOutAt,
        Instant newCheckOutAt,
        Long previousWorkedMinutes,
        Long newWorkedMinutes,
        String reason,
        AttendanceEmployeeResponse correctedBy,
        Instant correctedAt
) {}
