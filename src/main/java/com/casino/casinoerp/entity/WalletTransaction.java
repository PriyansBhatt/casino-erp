package com.casino.casinoerp.entity;

import jakarta.persistence.*;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "wallet_transactions", schema = "cashier")
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull(message = "Customer ID is required")
    @Column(name = "customer_id")
    private UUID customerId;

    @NotNull(message = "Customer session ID is required")
    @Column(name = "customer_session_id")
    private UUID customerSessionId;

    @NotBlank(message = "Transaction type is required")
    @Column(name = "transaction_type")
    private String transactionType;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1.00", message = "Amount must be greater than 0")
    private BigDecimal amount;

    @NotNull(message = "Business date is required")
    @Column(name = "business_date")
    private LocalDate businessDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    private String remarks;
}