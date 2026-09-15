package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.HotelBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.*;

public interface HotelBookingRepository extends JpaRepository<HotelBooking, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select h from HotelBooking h where h.id=:id")
    Optional<HotelBooking> lockById(UUID id);
    Optional<HotelBooking> findByIdempotencyKey(String idempotencyKey);
    List<HotelBooking> findByBusinessDateOrderByCreatedAtDescIdDesc(LocalDate businessDate);
}
