package com.casino.casinoerp.dto;

import jakarta.validation.constraints.*;

public record CreateLeaveTypeRequest(
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 1000) String description,
        boolean active
) {}
