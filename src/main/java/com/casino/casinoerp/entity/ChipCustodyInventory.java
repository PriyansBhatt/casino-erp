package com.casino.casinoerp.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.util.UUID;

@Data
@Entity
@Table(name = "chip_custody_inventory", schema = "cashier",
        uniqueConstraints = @UniqueConstraint(name = "uq_chip_custody_inventory_location_denomination",
                columnNames = {"location_key", "denomination"}))
public class ChipCustodyInventory {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "location_key", nullable = false, updatable = false)
    private String locationKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "location_type", nullable = false, updatable = false)
    private ChipCustodyLocationType locationType;

    @Column(name = "reference_id", updatable = false)
    private UUID referenceId;

    @Column(nullable = false, updatable = false)
    private Integer denomination;

    @Column(nullable = false)
    private Long quantity;
}
