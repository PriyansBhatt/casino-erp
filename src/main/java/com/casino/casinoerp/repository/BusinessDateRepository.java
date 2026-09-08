package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.BusinessDate;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface BusinessDateRepository extends JpaRepository<BusinessDate, UUID> {

    Optional<BusinessDate> findByBusinessDate(LocalDate businessDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select value from BusinessDate value where value.businessDate = :businessDate")
    Optional<BusinessDate> findByBusinessDateForUpdate(LocalDate businessDate);

    List<BusinessDate> findByStatus(String status);

    @Query(value = "select 1 from pg_advisory_xact_lock(42424220260908)", nativeQuery = true)
    Integer acquireLifecycleLock();
}
