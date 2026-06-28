package com.casino.casinoerp.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "pit_table_transactions", schema = "casino")
public class PitTableTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull(message = "Pit table ID is required")
    @Column(name = "pit_table_id")
    private UUID pitTableId;

    @NotNull(message = "Business date is required")
    @Column(name = "business_date")
    private LocalDate businessDate;

    @NotBlank(message = "Transaction type is required")
    @Column(name = "transaction_type")
    private String transactionType;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1.00", message = "Amount must be greater than 0")
    private BigDecimal amount;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    private String remarks;
}