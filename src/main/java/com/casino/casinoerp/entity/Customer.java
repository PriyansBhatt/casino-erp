package com.casino.casinoerp.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "customers", schema = "customer")
public class Customer {

    @Id
    private UUID id;

    @NotBlank(message = "Customer code is required")
    @Column(name = "customer_code")
    private String customerCode;

    @NotBlank(message = "Full name is required")
    @Column(name = "full_name")
    private String fullName;

    @NotBlank(message = "Phone number is required")
    @Pattern(
            regexp = "^[0-9+\\- ]{7,20}$",
            message = "Invalid phone number"
    )
    private String phone;

    @NotBlank(message = "Nationality is required")
    private String nationality;

    @NotNull(message = "Status is required")
    @Enumerated(EnumType.STRING)
    private CustomerStatus status;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    private String gender;

    @Column(name = "permanent_address")
    private String permanentAddress;

    @Column(name = "current_address")
    private String currentAddress;

    private String email;

    private String occupation;

    @Enumerated(EnumType.STRING)
    private CustomerCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level")
    private CustomerRiskLevel riskLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status")
    private KycStatus kycStatus;

    @Column(name = "internal_notes")
    private String internalNotes;

    @Column(name = "primary_photo_attachment_id")
    private UUID primaryPhotoAttachmentId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
