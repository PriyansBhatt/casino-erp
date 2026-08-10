package com.casino.casinoerp.dto;

import com.casino.casinoerp.entity.IdentityDocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record IdentityDocumentRequest(
        @NotNull(message = "Document type is required")
        IdentityDocumentType documentType,

        @NotBlank(message = "Document number is required")
        @Size(max = 100, message = "Document number must not exceed 100 characters")
        String documentNumber,

        @Size(max = 100, message = "Issuing country must not exceed 100 characters")
        String issuingCountry,

        LocalDate issuedDate,
        LocalDate expiryDate,
        Boolean primaryDocument
) {
}
