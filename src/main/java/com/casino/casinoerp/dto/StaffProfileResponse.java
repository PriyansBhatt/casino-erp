package com.casino.casinoerp.dto;

import com.casino.casinoerp.entity.*;
import java.time.*;
import java.util.UUID;

public record StaffProfileResponse(
        UUID staffProfileId, UUID userId, String username, String fullName, String employeeCode,
        HrMasterDataResponse department, HrMasterDataResponse jobTitle,
        EmploymentStatus employmentStatus, EmploymentType employmentType, LocalDate dateOfJoining,
        String phone, StaffManagerSummary reportingManager, String remarks,
        LocalDateTime createdAt, LocalDateTime updatedAt
) {
    public record StaffManagerSummary(UUID staffProfileId, String employeeCode, String username, String fullName) {}
}
