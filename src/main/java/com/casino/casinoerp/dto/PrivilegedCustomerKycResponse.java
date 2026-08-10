package com.casino.casinoerp.dto;

import com.casino.casinoerp.entity.CustomerCategory;
import com.casino.casinoerp.entity.CustomerRiskLevel;
import com.casino.casinoerp.entity.KycStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PrivilegedCustomerKycResponse(
        UUID id,
        String customerCode,
        String fullName,
        String phone,
        String nationality,
        String status,
        LocalDate dateOfBirth,
        String gender,
        String permanentAddress,
        String currentAddress,
        String email,
        String occupation,
        KycStatus kycStatus,
        CustomerCategory category,
        CustomerRiskLevel riskLevel,
        String internalNotes,
        List<PrivilegedIdentityDocumentResponse> identityDocuments,
        List<CustomerAttachmentMetadataResponse> attachments,
        long totalVisits,
        LocalDate lastVisitBusinessDate,
        LocalDateTime lastEntryTime,
        boolean hasActiveSession,
        UUID activeSessionId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
