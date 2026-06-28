package com.casino.casinoerp.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "customer_chip_wallets", schema = "cashier")
public class CustomerWallet {

    @Id
    private UUID id;

    @Column(name = "customer_session_id")
    private UUID customerSessionId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "total_buy_in")
    private BigDecimal totalBuyIn;

    @Column(name = "total_cash_out")
    private BigDecimal totalCashOut;

    @Column(name = "total_table_win")
    private BigDecimal totalTableWin;

    @Column(name = "total_table_loss")
    private BigDecimal totalTableLoss;

    @Column(name = "total_machine_win")
    private BigDecimal totalMachineWin;

    @Column(name = "total_machine_loss")
    private BigDecimal totalMachineLoss;

    @Column(name = "transfer_in")
    private BigDecimal transferIn;

    @Column(name = "transfer_out")
    private BigDecimal transferOut;

    @Column(name = "expected_chip_position")
    private BigDecimal expectedChipPosition;

    @Column(name = "unresolved_chip_exposure")
    private BigDecimal unresolvedChipExposure;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}