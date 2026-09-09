package com.casino.casinoerp.dto;

import com.casino.casinoerp.entity.*;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;

public record CreateStaffProfileRequest(
        @NotNull UUID userId,
        @NotBlank @Size(max = 50) String employeeCode,
        @NotNull UUID departmentId,
        @NotNull UUID jobTitleId,
        @NotNull EmploymentStatus employmentStatus,
        @NotNull EmploymentType employmentType,
        @NotNull LocalDate dateOfJoining,
        @Size(max = 50) String phone,
        UUID reportingManagerStaffProfileId,
        @Size(max = 1000) String remarks
) {}
