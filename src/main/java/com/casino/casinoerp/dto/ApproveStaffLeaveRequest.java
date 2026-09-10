package com.casino.casinoerp.dto;

import jakarta.validation.constraints.Size;

public record ApproveStaffLeaveRequest(
        @Size(max = 500) String remarks
) {}
