package com.casino.casinoerp.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Data
@Entity
@Table(name = "customer_bonuses", schema = "customer")
public class CustomerBonus {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "bonus_code", nullable = false) private String bonusCode;
    @Column(name = "customer_id", nullable = false) private UUID customerId;
    @Column(name = "customer_session_id", nullable = false) private UUID customerSessionId;
    @Column(name = "business_date", nullable = false) private LocalDate businessDate;
    @Enumerated(EnumType.STRING) @Column(name = "bonus_type", nullable = false) private CustomerBonusType bonusType;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal amount;
    @Column(nullable = false, length = 1000) private String reason;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private CustomerBonusStatus status;
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Column(name = "approved_by", nullable = false) private UUID approvedBy;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "approved_at", nullable = false) private LocalDateTime approvedAt;
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name = "idempotency_key", nullable = false) private String idempotencyKey;
}
