package com.casino.casinoerp.dto;

import jakarta.validation.constraints.*;

public record CancelStaffRosterRequest(@NotBlank @Size(max=1000) String reason) {}
