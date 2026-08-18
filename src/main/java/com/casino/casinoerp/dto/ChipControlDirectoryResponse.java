package com.casino.casinoerp.dto;

import java.time.LocalDate;
import java.util.List;

public record ChipControlDirectoryResponse(
        LocalDate businessDate,
        List<ChipControlSessionResponse> sessions
) {}
