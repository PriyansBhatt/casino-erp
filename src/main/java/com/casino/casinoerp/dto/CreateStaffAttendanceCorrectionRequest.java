package com.casino.casinoerp.dto;

import com.casino.casinoerp.entity.StaffAttendanceCorrectionType;
import jakarta.validation.constraints.*;

import java.time.Instant;

public record CreateStaffAttendanceCorrectionRequest(
        @NotNull StaffAttendanceCorrectionType type,
        Instant checkInAt,
        Instant checkOutAt,
        @NotBlank @Size(max = 500) String reason
) {}
