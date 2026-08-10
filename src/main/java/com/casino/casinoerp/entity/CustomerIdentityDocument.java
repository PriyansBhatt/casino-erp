package com.casino.casinoerp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "customer_identity_documents", schema = "customer")
public class CustomerIdentityDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "id_type", nullable = false, length = 40)
    private IdentityDocumentType documentType;

    @Column(name = "id_number", nullable = false, length = 100)
    private String documentNumber;

    @Column(name = "normalized_id_number", nullable = false, length = 100)
    private String normalizedDocumentNumber;

    @Column(name = "issuing_country", length = 100)
    private String issuingCountry;

    @Column(name = "issued_date")
    private LocalDate issuedDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attachment_id")
    private CustomerAttachment attachment;

    @Column(name = "is_primary", nullable = false)
    private boolean primaryDocument;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_status", nullable = false, length = 30)
    private IdentityDocumentStatus status = IdentityDocumentStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by")
    private User verifiedBy;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
