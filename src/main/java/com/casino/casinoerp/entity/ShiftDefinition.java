package com.casino.casinoerp.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.*;
import java.util.UUID;

@Data @Entity
@Table(name="shift_definitions",schema="casino")
public class ShiftDefinition {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(nullable=false,length=50) private String code;
    @Column(nullable=false,length=150) private String name;
    @Column(length=1000) private String description;
    @Column(name="start_time",nullable=false) private LocalTime startTime;
    @Column(name="end_time",nullable=false) private LocalTime endTime;
    @Column(name="crosses_midnight",nullable=false) private boolean crossesMidnight;
    @Column(name="late_grace_minutes",nullable=false) private int lateGraceMinutes;
    @Column(name="early_check_in_minutes",nullable=false) private int earlyCheckInMinutes;
    @Column(nullable=false) private boolean active;
    @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
    @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt;
}
