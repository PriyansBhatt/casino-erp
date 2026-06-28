package com.casino.casinoerp.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "customer_sessions", schema = "session")
public class CustomerSession {

    @Id
    private UUID id;

    @Column(name = "session_code")
    private String sessionCode;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "session_date")
    private LocalDate sessionDate;

    @Column(name = "entry_time")
    private LocalDateTime entryTime;

    @Column(name = "exit_time")
    private LocalDateTime exitTime;

    private String status;

    @Column(name = "opened_by")
    private UUID openedBy;

    @Column(name = "closed_by")
    private UUID closedBy;

    @Column(name = "total_buy_in")
    private BigDecimal totalBuyIn;

    @Column(name = "total_cash_out")
    private BigDecimal totalCashOut;

    @Column(name = "business_date")
    private LocalDate businessDate;

    @Column(name = "unresolved_chip_exposure")
    private BigDecimal unresolvedChipExposure;

    private String remarks;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}