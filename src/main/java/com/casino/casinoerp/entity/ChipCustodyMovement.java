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
@Table(name = "chip_custody_movements", schema = "cashier")
public class ChipCustodyMovement {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, updatable = false)
    private ChipCustodyMovementType movementType;

    @Column(name = "business_date", nullable = false, updatable = false)
    private LocalDate businessDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, updatable = false)
    private ChipCustodyLocationType sourceType;

    @Column(name = "source_reference_id", updatable = false)
    private UUID sourceReferenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "destination_type", nullable = false, updatable = false)
    private ChipCustodyLocationType destinationType;

    @Column(name = "destination_reference_id", updatable = false)
    private UUID destinationReferenceId;

    @Column(name = "related_transaction_type", updatable = false)
    private String relatedTransactionType;

    @Column(name = "related_transaction_id", updatable = false)
    private UUID relatedTransactionId;

    @Column(name = "customer_session_id", updatable = false)
    private UUID customerSessionId;

    @Column(name = "pit_table_id", updatable = false)
    private UUID pitTableId;

    @Column(name = "total_value", nullable = false, updatable = false)
    private BigDecimal totalValue;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "chip_custody_movement_lines", schema = "cashier",
            joinColumns = @JoinColumn(name = "movement_id"))
    @MapKeyColumn(name = "denomination")
    @Column(name = "quantity", nullable = false)
    private Map<Integer, Long> denominations = new LinkedHashMap<>();
}
