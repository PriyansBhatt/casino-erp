package com.casino.casinoerp.entity;

import jakarta.persistence.*;
import lombok.Data;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "chip_buy_ins", schema = "cashier")
public class ChipBuyIn {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank(message = "Buy-in code is required")
    @Column(name = "buy_in_code")
    private String buyInCode;

    @NotNull(message = "Customer session ID is required")
    @Column(name = "customer_session_id")
    private UUID customerSessionId;

    @NotNull(message = "Customer ID is required")
    @Column(name = "customer_id")
    private UUID customerId;

    @NotNull(message = "Amount received is required")
    @DecimalMin(value = "1.00", message = "Amount received must be greater than 0")
    @Column(name = "amount_received")
    private BigDecimal amountReceived;

    @NotBlank(message = "Payment mode is required")
    @Column(name = "payment_mode")
    private String paymentMode;

    @NotNull(message = "Total chip value issued is required")
    @DecimalMin(value = "1.00", message = "Total chip value issued must be greater than 0")
    @Column(name = "total_chip_value_issued")
    private BigDecimal totalChipValueIssued;

    @Column(name = "high_value_alert")
    private Boolean highValueAlert;

    @Column(name = "supervisor_approved_by")
    private UUID supervisorApprovedBy;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "business_date")
    private LocalDate businessDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    private String remarks;
}