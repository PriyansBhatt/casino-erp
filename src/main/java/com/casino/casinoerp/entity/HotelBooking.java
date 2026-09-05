package com.casino.casinoerp.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Data @Entity
@Table(name = "hotel_bookings", schema = "customer")
public class HotelBooking {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name="booking_code", nullable=false) private String bookingCode;
    @Column(name="customer_id", nullable=false) private UUID customerId;
    @Column(name="customer_session_id") private UUID customerSessionId;
    @Column(name="business_date", nullable=false) private LocalDate businessDate;
    @Column(name="hotel_name", nullable=false) private String hotelName;
    @Column(name="room_type", nullable=false) private String roomType;
    @Column(name="check_in_date", nullable=false) private LocalDate checkInDate;
    @Column(name="check_out_date", nullable=false) private LocalDate checkOutDate;
    @Column(name="number_of_guests", nullable=false) private Integer numberOfGuests;
    @Column(name="estimated_cost", nullable=false, precision=19, scale=2) private BigDecimal estimatedCost;
    @Column(name="actual_cost", precision=19, scale=2) private BigDecimal actualCost;
    @Enumerated(EnumType.STRING) @Column(name="billing_type", nullable=false) private HotelBillingType billingType;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private HotelBookingStatus status;
    @Column(length=1000) private String remarks;
    @Column(name="created_at", nullable=false) private LocalDateTime createdAt;
    @Column(name="updated_at", nullable=false) private LocalDateTime updatedAt;
    @Column(name="created_by", nullable=false) private UUID createdBy;
    @Column(name="approved_by") private UUID approvedBy;
    @Column(name="approved_at") private LocalDateTime approvedAt;
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name="idempotency_key", nullable=false) private String idempotencyKey;
}
