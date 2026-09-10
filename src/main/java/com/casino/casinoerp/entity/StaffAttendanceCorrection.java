package com.casino.casinoerp.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Table(name = "staff_attendance_corrections", schema = "core")
public class StaffAttendanceCorrection {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "attendance_id", nullable = false)
    private UUID attendanceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "correction_type", nullable = false, length = 30)
    private StaffAttendanceCorrectionType correctionType;

    @Column(name = "previous_check_in_at") private Instant previousCheckInAt;
    @Column(name = "new_check_in_at") private Instant newCheckInAt;
    @Column(name = "previous_check_out_at") private Instant previousCheckOutAt;
    @Column(name = "new_check_out_at") private Instant newCheckOutAt;
    @Column(name = "previous_worked_minutes") private Long previousWorkedMinutes;
    @Column(name = "new_worked_minutes") private Long newWorkedMinutes;
    @Column(nullable = false, length = 500) private String reason;
    @Column(name = "corrected_by_user_id", nullable = false) private UUID correctedByUserId;
    @Column(name = "corrected_at", nullable = false) private Instant correctedAt;
}
