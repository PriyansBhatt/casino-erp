package com.casino.casinoerp.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "staff_leave_requests", schema = "casino")
public class StaffLeaveRequest {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "staff_profile_id", nullable = false) private UUID staffProfileId;
    @Column(name = "leave_type_id", nullable = false) private UUID leaveTypeId;
    @Column(name = "start_date", nullable = false) private LocalDate startDate;
    @Column(name = "end_date", nullable = false) private LocalDate endDate;
    @Column(nullable = false, length = 1000) private String reason;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private LeaveRequestStatus status;
    @Column(name = "submitted_at", nullable = false) private LocalDateTime submittedAt;
    @Column(name = "reviewed_by_user_id") private UUID reviewedByUserId;
    @Column(name = "reviewed_at") private LocalDateTime reviewedAt;
    @Column(name = "review_reason", length = 500) private String reviewReason;
    @Column(name = "cancelled_by_user_id") private UUID cancelledByUserId;
    @Column(name = "cancelled_at") private LocalDateTime cancelledAt;
    @Column(name = "cancellation_reason", length = 500) private String cancellationReason;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
}
