package com.casino.casinoerp.dto;

import com.casino.casinoerp.entity.IdentityDocumentStatus;
import com.casino.casinoerp.entity.IdentityDocumentType;

import java.time.LocalDate;
import java.util.UUID;

public record IdentityDocumentResponse(
        UUID id,
        IdentityDocumentType documentType,
        String documentNumber,
        String issuingCountry,
        LocalDate issuedDate,
        LocalDate expiryDate,
        boolean primaryDocument,
        IdentityDocumentStatus status
) {
}
