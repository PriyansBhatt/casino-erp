package com.casino.casinoerp.dto;

import com.casino.casinoerp.entity.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

public record HotelBookingResponse(
        UUID id, String bookingCode, UUID customerId, String customerCode, String customerName,
        UUID customerSessionId, String sessionCode, LocalDate businessDate, String hotelName,
        String roomType, LocalDate checkInDate, LocalDate checkOutDate, Integer numberOfGuests,
        BigDecimal estimatedCost, BigDecimal actualCost, HotelBillingType billingType,
        HotelBookingStatus status, String remarks, ActorReferenceResponse createdBy,
        ActorReferenceResponse approvedBy, LocalDateTime createdAt, LocalDateTime updatedAt,
        LocalDateTime approvedAt
) {}
