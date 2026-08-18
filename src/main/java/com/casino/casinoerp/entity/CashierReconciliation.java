package com.casino.casinoerp.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Data
@Entity
@Table(name = "cashier_reconciliations", schema = "cashier")
public class CashierReconciliation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "business_date", nullable = false) private LocalDate businessDate;
    @Column(name = "cashier_user_id", nullable = false) private UUID cashierUserId;
    @Column(name = "opening_cash", nullable = false) private BigDecimal openingCash;
    @Column(name = "actual_closing_cash", nullable = false) private BigDecimal actualClosingCash;
    @Column(name = "expected_closing_cash", nullable = false) private BigDecimal expectedClosingCash;
    @Column(nullable = false) private BigDecimal variance;
    @Column(nullable = false) private String status;
    @Column(name = "lifecycle_status", nullable = false) private String lifecycleStatus;
    @Column(name = "idempotency_key", nullable = false) private String idempotencyKey;
    @Column(name = "submitted_at", nullable = false) private LocalDateTime submittedAt;
    private String remarks;
    @Column(name = "reopened_at") private LocalDateTime reopenedAt;
    @Column(name = "reopened_by") private UUID reopenedBy;
    @Column(name = "reopen_reason") private String reopenReason;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "cashier_reconciliation_denominations", schema = "cashier",
            joinColumns = @JoinColumn(name = "reconciliation_id"))
    @MapKeyColumn(name = "denomination")
    @Column(name = "quantity")
    private Map<Integer, Integer> denominations = new LinkedHashMap<>();
}
