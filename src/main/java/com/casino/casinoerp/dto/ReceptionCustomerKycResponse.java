package com.casino.casinoerp.dto;

import com.casino.casinoerp.entity.KycStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ReceptionCustomerKycResponse(
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
        IdentityDocumentResponse primaryIdentityDocument,
        boolean photoAvailable,
        UUID primaryPhotoAttachmentId,
        long totalVisits,
        LocalDate lastVisitBusinessDate,
        LocalDateTime lastEntryTime,
        boolean hasActiveSession,
        UUID activeSessionId
) {
}
