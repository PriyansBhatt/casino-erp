package com.casino.casinoerp.dto;

import com.casino.casinoerp.entity.CustomerAttachmentStatus;
import com.casino.casinoerp.entity.CustomerAttachmentType;

import java.time.LocalDateTime;
import java.util.UUID;

public record CustomerAttachmentMetadataResponse(
        UUID id,
        CustomerAttachmentType attachmentType,
        String originalFilename,
        String contentType,
        long sizeBytes,
        CustomerAttachmentStatus status,
        ActorReferenceResponse uploadedBy,
        LocalDateTime uploadedAt
) {
}
