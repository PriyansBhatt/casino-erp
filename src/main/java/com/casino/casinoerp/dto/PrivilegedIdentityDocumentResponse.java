package com.casino.casinoerp.dto;

import com.casino.casinoerp.entity.IdentityDocumentStatus;
import com.casino.casinoerp.entity.IdentityDocumentType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record PrivilegedIdentityDocumentResponse(
        UUID id,
        IdentityDocumentType documentType,
        String documentNumber,
        String issuingCountry,
        LocalDate issuedDate,
        LocalDate expiryDate,
        boolean primaryDocument,
        IdentityDocumentStatus status,
        CustomerAttachmentMetadataResponse attachment,
        ActorReferenceResponse createdBy,
        LocalDateTime createdAt,
        ActorReferenceResponse verifiedBy,
        LocalDateTime verifiedAt,
        LocalDateTime updatedAt
) {
}
