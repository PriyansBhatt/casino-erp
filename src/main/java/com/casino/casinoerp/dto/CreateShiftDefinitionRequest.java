package com.casino.casinoerp.dto;

import jakarta.validation.constraints.*;
import java.time.LocalTime;

public record CreateShiftDefinitionRequest(@NotBlank @Size(max=50) String code,
        @NotBlank @Size(max=150) String name,@Size(max=1000) String description,
        @NotNull LocalTime startTime,@NotNull LocalTime endTime,@NotNull Boolean crossesMidnight,
        @NotNull @PositiveOrZero Integer lateGraceMinutes,
        @NotNull @PositiveOrZero Integer earlyCheckInMinutes,Boolean active) {}
