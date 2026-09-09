package com.casino.casinoerp.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.*;
import java.util.UUID;

@Data @Entity
@Table(name="staff_roster_assignments",schema="casino")
public class StaffRosterAssignment {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="staff_profile_id",nullable=false) private UUID staffProfileId;
    @Column(name="shift_definition_id",nullable=false) private UUID shiftDefinitionId;
    @Column(name="roster_date",nullable=false) private LocalDate rosterDate;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private RosterStatus status;
    @Column(length=1000) private String remarks;
    @Column(name="cancellation_reason",length=1000) private String cancellationReason;
    @Column(name="cancelled_by") private UUID cancelledBy;
    @Column(name="cancelled_at") private LocalDateTime cancelledAt;
    @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
    @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt;
}
