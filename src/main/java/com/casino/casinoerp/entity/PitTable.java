package com.casino.casinoerp.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "pit_tables", schema = "casino")
public class PitTable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "physical_table_id", nullable = false, updatable = false)
    private UUID physicalTableId;

    @Column(name = "table_code")
    private String tableCode;

    @Column(name = "table_name")
    private String tableName;

    @Column(name = "game_type")
    private String gameType;

    @Column(name = "max_players")
    private Integer maxPlayers;

    private String status;

    @Column(name = "dealer_id")
    private UUID dealerId;

    @Column(name = "supervisor_id")
    private UUID supervisorId;

    @Column(name = "business_date")
    private LocalDate businessDate;

    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "opening_float")
    private BigDecimal openingFloat;

    @Column(name = "closing_float")
    private BigDecimal closingFloat;

    private String remarks;

    @Column(name = "opening_idempotency_key", updatable = false)
    private String openingIdempotencyKey;
}
