package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.CustomerServiceRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface CustomerServiceRecordRepository
        extends JpaRepository<CustomerServiceRecord, UUID> {

    List<CustomerServiceRecord> findByCustomerId(UUID customerId);

    List<CustomerServiceRecord> findByCustomerSessionId(UUID customerSessionId);

    List<CustomerServiceRecord> findByBusinessDate(LocalDate businessDate);
}