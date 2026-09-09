package com.casino.casinoerp.dto;

import java.time.*;
import java.util.UUID;

public record ShiftDefinitionResponse(UUID id,String code,String name,String description,
        LocalTime startTime,LocalTime endTime,boolean crossesMidnight,int lateGraceMinutes,
        int earlyCheckInMinutes,boolean active,LocalDateTime createdAt,LocalDateTime updatedAt) {}
