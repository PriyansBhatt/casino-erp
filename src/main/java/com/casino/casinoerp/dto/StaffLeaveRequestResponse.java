package com.casino.casinoerp.dto;

import com.casino.casinoerp.entity.LeaveRequestStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record StaffLeaveRequestResponse(
        UUID requestId,
        StaffSummary staff,
        LeaveTypeSummary leaveType,
        LocalDate startDate,
        LocalDate endDate,
        long calendarDays,
        String reason,
        LeaveRequestStatus status,
        ActorSummary reviewedBy,
        LocalDateTime reviewedAt,
        String reviewReason,
        ActorSummary cancelledBy,
        LocalDateTime cancelledAt,
        String cancellationReason,
        LocalDateTime submittedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record StaffSummary(UUID staffProfileId, UUID userId, String employeeCode,
                               String username, String fullName) {}
    public record LeaveTypeSummary(UUID id, String code, String name) {}
    public record ActorSummary(UUID id, String username, String fullName) {}
}
