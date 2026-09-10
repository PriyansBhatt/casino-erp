package com.casino.casinoerp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectStaffLeaveRequest(
        @NotBlank @Size(max = 500) String reason
) {}
