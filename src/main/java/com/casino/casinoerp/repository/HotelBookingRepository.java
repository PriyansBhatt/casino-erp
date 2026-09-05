package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.HotelBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.*;

public interface HotelBookingRepository extends JpaRepository<HotelBooking, UUID> {
    Optional<HotelBooking> findByIdempotencyKey(String idempotencyKey);
    List<HotelBooking> findByBusinessDateOrderByCreatedAtDesc(LocalDate businessDate);
}
