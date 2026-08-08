package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    Optional<Customer> findByCustomerCode(String customerCode);

    List<Customer> findByCustomerCodeContainingIgnoreCaseOrFullNameContainingIgnoreCaseOrPhoneContainingIgnoreCase(
            String customerCode,
            String fullName,
            String phone
    );
}
