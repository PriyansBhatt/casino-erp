package com.casino.casinoerp.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Entity
@Table(name = "verified_gaming_results", schema = "casino")
public class VerifiedGamingResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "customer_session_id", nullable = false)
    private UUID customerSessionId;

    @Column(name = "pit_table_id", nullable = false)
    private UUID pitTableId;

    @Column(name = "assignment_id", nullable = false)
    private UUID assignmentId;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private VerifiedGamingSourceType sourceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_type", nullable = false)
    private VerifiedGamingResultType resultType;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "verified_gaming_result_denominations",
            schema = "casino",
            joinColumns = @JoinColumn(name = "verified_gaming_result_id"))
    @MapKeyColumn(name = "denomination")
    @Column(name = "quantity", nullable = false)
    private Map<Integer, Integer> denominations = new LinkedHashMap<>();

    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
}
