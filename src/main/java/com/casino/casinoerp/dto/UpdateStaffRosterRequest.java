package com.casino.casinoerp.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;

public record UpdateStaffRosterRequest(@NotNull UUID shiftDefinitionId,@NotNull LocalDate rosterDate,
        @Size(max=1000) String remarks) {}
