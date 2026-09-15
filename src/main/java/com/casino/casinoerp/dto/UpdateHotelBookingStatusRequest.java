package com.casino.casinoerp.dto;

import com.casino.casinoerp.entity.HotelBookingStatus;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record UpdateHotelBookingStatusRequest(
        @NotNull HotelBookingStatus status,
        @DecimalMin(value="0.0", inclusive=true) @Digits(integer=17,fraction=2) BigDecimal actualCost
) {}
