package com.casino.casinoerp.dto;

import java.util.UUID;

public record AttendanceEmployeeResponse(
        UUID id,
        String username,
        String fullName
) {}
