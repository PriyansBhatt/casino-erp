package com.casino.casinoerp.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Data @Entity
@Table(name = "losing_returns", schema = "cashier")
public class LosingReturn {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name="losing_return_code", nullable=false) private String losingReturnCode;
    @Column(name="customer_id", nullable=false) private UUID customerId;
    @Column(name="customer_session_id", nullable=false) private UUID customerSessionId;
    @Column(name="business_date", nullable=false) private LocalDate businessDate;
    @Column(name="eligible_verified_loss", nullable=false) private BigDecimal eligibleVerifiedLoss;
    @Column(name="return_rate", nullable=false) private BigDecimal returnRate;
    @Column(name="amount_paid", nullable=false) private BigDecimal amountPaid;
    @Column(name="payment_mode", nullable=false) private String paymentMode;
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name="idempotency_key", nullable=false) private String idempotencyKey;
    @Column(name="created_at", nullable=false) private LocalDateTime createdAt;
    @Column(name="created_by", nullable=false) private UUID createdBy;
    private String remarks;
}
