package com.casino.casinoerp.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "pit_table_staff_assignments", schema = "casino")
public class PitTableStaffAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "pit_table_id", nullable = false)
    private UUID pitTableId;

    @Column(name = "staff_user_id", nullable = false)
    private UUID staffUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_role", nullable = false, length = 30)
    private PitTableStaffAssignmentRole assignmentRole;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "assigned_by", nullable = false)
    private UUID assignedBy;

    @Column(name = "ended_by")
    private UUID endedBy;

    @Column(columnDefinition = "text")
    private String remarks;

    @Column(name = "end_remarks", columnDefinition = "text")
    private String endRemarks;

    @Column(name = "assignment_idempotency_key", length = 100, unique = true)
    private String assignmentIdempotencyKey;

    @Column(name = "end_idempotency_key", length = 100, unique = true)
    private String endIdempotencyKey;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Transient
    public boolean isActive() {
        return endedAt == null;
    }
}
