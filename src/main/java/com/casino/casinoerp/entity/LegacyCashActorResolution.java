package com.casino.casinoerp.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "legacy_cash_actor_resolutions", schema = "cashier")
public class LegacyCashActorResolution {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private UUID actorUserId;

    @Column(name = "business_date", nullable = false, updatable = false)
    private LocalDate businessDate;

    @Column(name = "cash_received", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal cashReceived;

    @Column(name = "cash_paid", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal cashPaid;

    @Column(name = "net_cash_movement", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal netCashMovement;

    @Column(name = "opening_cash_verification", nullable = false, updatable = false)
    private String openingCashVerification;

    @Column(nullable = false, length = 500, updatable = false)
    private String reason;

    @Column(name = "resolved_by", nullable = false, updatable = false)
    private UUID resolvedBy;

    @Column(name = "resolved_at", nullable = false, updatable = false)
    private LocalDateTime resolvedAt;

    @Column(name = "idempotency_key", nullable = false, length = 100, updatable = false)
    private String idempotencyKey;
}
