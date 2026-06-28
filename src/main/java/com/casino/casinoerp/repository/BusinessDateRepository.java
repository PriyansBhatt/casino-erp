package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.BusinessDate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface BusinessDateRepository extends JpaRepository<BusinessDate, UUID> {

    Optional<BusinessDate> findByBusinessDate(LocalDate businessDate);

    List<BusinessDate> findByStatus(String status);
}