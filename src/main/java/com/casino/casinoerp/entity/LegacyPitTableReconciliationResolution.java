package com.casino.casinoerp.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "legacy_pit_table_reconciliation_resolutions", schema = "casino")
public class LegacyPitTableReconciliationResolution {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "pit_table_id", nullable = false, updatable = false)
    private UUID pitTableId;

    @Column(name = "business_date", nullable = false, updatable = false)
    private LocalDate businessDate;

    @Column(name = "original_opening_float", nullable = false, updatable = false)
    private BigDecimal originalOpeningFloat;

    @Column(name = "physical_closing_float", nullable = false, updatable = false)
    private BigDecimal physicalClosingFloat;

    @Column(name = "legacy_opening_float_gap", nullable = false, updatable = false)
    private BigDecimal legacyOpeningFloatGap;

    @Column(name = "opening_float_verification", nullable = false, updatable = false)
    private String openingFloatVerification;

    @Column(nullable = false, length = 500, updatable = false)
    private String reason;

    @Column(name = "resolved_by", nullable = false, updatable = false)
    private UUID resolvedBy;

    @Column(name = "resolved_at", nullable = false, updatable = false)
    private LocalDateTime resolvedAt;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;
}
