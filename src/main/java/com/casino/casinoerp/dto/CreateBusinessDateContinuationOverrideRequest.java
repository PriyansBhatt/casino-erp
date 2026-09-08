package com.casino.casinoerp.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateBusinessDateContinuationOverrideRequest(
        @NotBlank(message = "Continuation reason is required")
        @Size(max = 500, message = "Continuation reason must not exceed 500 characters")
        String reason,
        @NotNull(message = "Duration in minutes is required")
        @Min(value = 1, message = "Duration must be at least 1 minute")
        @Max(value = 60, message = "Duration must not exceed 60 minutes")
        Integer durationMinutes) {
}
