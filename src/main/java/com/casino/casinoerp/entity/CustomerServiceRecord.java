package com.casino.casinoerp.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "customer_service_records", schema = "customer")
public class CustomerServiceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "customer_session_id")
    private UUID customerSessionId;

    @Column(name = "business_date")
    private LocalDate businessDate;

    @Column(name = "service_type")
    private String serviceType;

    @Column(name = "service_description")
    private String serviceDescription;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "provided_by")
    private UUID providedBy;

    @Column(name = "service_cost")
    private BigDecimal serviceCost;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    private String remarks;
}