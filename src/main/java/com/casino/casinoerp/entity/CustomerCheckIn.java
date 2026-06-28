package com.casino.casinoerp.entity;

import jakarta.persistence.*;
import lombok.Data;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "customer_checkins", schema = "reception")
public class CustomerCheckIn {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "customer_session_id")
    private UUID customerSessionId;

    @Column(name = "checkin_code")
    private String checkinCode;

    @Column(name = "entry_time")
    private LocalDateTime entryTime;

    @Column(name = "entry_reason")
    private String entryReason;

    @Column(name = "business_date")
    private LocalDate businessDate;

    @Column(name = "customer_condition")
    private String customerCondition;

    @Column(name = "id_verified")
    private Boolean idVerified;

    @Column(name = "badge_issued")
    private Boolean badgeIssued;

    private String status;

    private String remarks;
}