package com.casino.casinoerp.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "physical_pit_tables", schema = "casino")
public class PhysicalPitTable {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "table_code", nullable = false, length = 50, updatable = false)
    private String tableCode;

    @Column(name = "table_name", nullable = false, length = 100)
    private String tableName;

    @Column(name = "game_type", nullable = false, length = 50)
    private String gameType;

    @Column(name = "max_players")
    private Integer maxPlayers;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
