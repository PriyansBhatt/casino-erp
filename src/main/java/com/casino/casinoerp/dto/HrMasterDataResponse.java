package com.casino.casinoerp.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record HrMasterDataResponse(UUID id, String code, String name, String description,
        boolean active, Integer sortOrder, LocalDateTime createdAt, LocalDateTime updatedAt) {}
