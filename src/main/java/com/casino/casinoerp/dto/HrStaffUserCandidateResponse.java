package com.casino.casinoerp.dto;

import java.util.UUID;

public record HrStaffUserCandidateResponse(
        UUID userId,
        String username,
        String fullName,
        String role,
        String status
) {}
