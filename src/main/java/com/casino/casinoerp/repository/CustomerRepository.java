package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    Optional<Customer> findByCustomerCode(String customerCode);

    boolean existsByCustomerCode(String customerCode);

    @Query(value = """
            select exists (
                select 1
                from customer.customers
                where replace(replace(trim(phone), ' ', ''), '-', '') = :normalizedPhone
            )
            """, nativeQuery = true)
    boolean existsByNormalizedPhone(@Param("normalizedPhone") String normalizedPhone);

    @Query(value = """
            select max(cast(substring(customer_code from 5) as integer))
            from customer.customers
            where customer_code ~ '^CUS-[0-9]+$'
            """, nativeQuery = true)
    Integer findMaximumCustomerCodeNumber();

    List<Customer> findByCustomerCodeContainingIgnoreCaseOrFullNameContainingIgnoreCaseOrPhoneContainingIgnoreCase(
            String customerCode,
            String fullName,
            String phone
    );
}
