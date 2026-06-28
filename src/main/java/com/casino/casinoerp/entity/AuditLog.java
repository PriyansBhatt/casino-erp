package com.casino.casinoerp.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "audit_logs", schema = "audit")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_date")
    private LocalDate businessDate;

    @Column(name = "action_type")
    private String actionType;

    @Column(name = "module_name")
    private String moduleName;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "performed_by")
    private UUID performedBy;

    @Column(name = "performed_at")
    private LocalDateTime performedAt;

    private String remarks;
}