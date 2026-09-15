package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.CustomerServiceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface CustomerServiceRecordRepository
        extends JpaRepository<CustomerServiceRecord, UUID> {

    @Query(value="select * from customer.customer_service_records where not crm_record and customer_id=:customerId",nativeQuery=true)
    List<CustomerServiceRecord> findByCustomerId(UUID customerId);

    @Query(value="select * from customer.customer_service_records where not crm_record and customer_session_id=:customerSessionId",nativeQuery=true)
    List<CustomerServiceRecord> findByCustomerSessionId(UUID customerSessionId);

    @Query(value="select * from customer.customer_service_records where not crm_record and business_date=:businessDate",nativeQuery=true)
    List<CustomerServiceRecord> findByBusinessDate(LocalDate businessDate);
    @Override @Query(value="select * from customer.customer_service_records where not crm_record",nativeQuery=true)
    List<CustomerServiceRecord> findAll();
}