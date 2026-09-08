package com.casino.casinoerp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Entity
@Table(name = "business_date_continuation_overrides", schema = "casino")
public class BusinessDateContinuationOverride {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "authorized_by", nullable = false)
    private UUID authorizedBy;

    @Column(name = "authorized_at", nullable = false)
    private Instant authorizedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by")
    private UUID revokedBy;

    @Column(name = "revoke_reason", length = 500)
    private String revokeReason;
}
