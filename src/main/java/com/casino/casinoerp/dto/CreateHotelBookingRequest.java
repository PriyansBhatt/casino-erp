package com.casino.casinoerp.dto;

import com.casino.casinoerp.entity.HotelBillingType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateHotelBookingRequest(
        @NotNull UUID customerId,
        UUID customerSessionId,
        @NotBlank @Size(max=200) String hotelName,
        @NotBlank @Size(max=100) String roomType,
        @NotNull LocalDate checkInDate,
        @NotNull LocalDate checkOutDate,
        @NotNull @Min(1) Integer numberOfGuests,
        @NotNull @DecimalMin(value="0.0", inclusive=true) @Digits(integer=17,fraction=2) BigDecimal estimatedCost,
        @NotNull HotelBillingType billingType,
        @Size(max=1000) String remarks,
        @NotBlank @Size(max=100) String idempotencyKey
) {}
