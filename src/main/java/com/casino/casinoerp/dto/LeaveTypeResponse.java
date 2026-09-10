package com.casino.casinoerp.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record LeaveTypeResponse(
        UUID id,
        String code,
        String name,
        String description,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
