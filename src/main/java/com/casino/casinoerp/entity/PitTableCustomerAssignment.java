package com.casino.casinoerp.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "pit_table_customer_assignments", schema = "casino")
public class PitTableCustomerAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "pit_table_id", nullable = false) private UUID pitTableId;
    @Column(name = "customer_id", nullable = false) private UUID customerId;
    @Column(name = "customer_session_id", nullable = false) private UUID customerSessionId;
    @Column(name = "business_date", nullable = false) private LocalDate businessDate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false) private PitTableCustomerAssignmentStatus status;
    @Column(name = "joined_at", nullable = false) private LocalDateTime joinedAt;
    @Column(name = "joined_by", nullable = false) private UUID joinedBy;
    @Column(name = "left_at") private LocalDateTime leftAt;
    @Column(name = "left_by") private UUID leftBy;
    @Column(name = "custody_settled_at") private LocalDateTime custodySettledAt;
    @Column(name = "custody_settlement_key", length = 100, unique = true)
    private String custodySettlementKey;
}
