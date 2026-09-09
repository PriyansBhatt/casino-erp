package com.casino.casinoerp.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Entity
@Table(name = "staff_attendance", schema = "core")
public class StaffAttendance {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StaffAttendanceStatus status;

    @Column(name = "check_in_at", nullable = false)
    private Instant checkInAt;

    @Column(name = "check_out_at")
    private Instant checkOutAt;

    @Column(name = "roster_assignment_id")
    private UUID rosterAssignmentId;

    @Column(name = "roster_date")
    private LocalDate rosterDate;

    @Column(name = "shift_code", length = 50)
    private String shiftCode;

    @Column(name = "shift_name", length = 150)
    private String shiftName;

    @Column(name = "scheduled_start_at")
    private Instant scheduledStartAt;

    @Column(name = "scheduled_end_at")
    private Instant scheduledEndAt;

    @Column(name = "roster_late_grace_minutes")
    private Integer rosterLateGraceMinutes;

    @Column(name = "roster_early_check_in_minutes")
    private Integer rosterEarlyCheckInMinutes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
