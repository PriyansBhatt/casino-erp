package com.casino.casinoerp.dto;

import jakarta.validation.constraints.*;

public record UpdateLeaveTypeRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 1000) String description,
        boolean active
) {}
