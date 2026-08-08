package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.ReceptionCustomerResponse;
import com.casino.casinoerp.entity.Customer;
import com.casino.casinoerp.exception.ResourceNotFoundException;
import com.casino.casinoerp.repository.CustomerRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }
    public ReceptionCustomerResponse getCustomerByCode(String customerCode) {
        return customerRepository
                .findByCustomerCode(customerCode)
                .map(this::toReceptionResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found."));
    }

    public List<ReceptionCustomerResponse> getAllCustomers() {
        return customerRepository.findAll()
                .stream()
                .map(this::toReceptionResponse)
                .toList();
    }

    public List<ReceptionCustomerResponse> searchCustomers(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Search query is required.");
        }

        String normalizedQuery = query.trim();
        return customerRepository
                .findByCustomerCodeContainingIgnoreCaseOrFullNameContainingIgnoreCaseOrPhoneContainingIgnoreCase(
                        normalizedQuery,
                        normalizedQuery,
                        normalizedQuery
                )
                .stream()
                .map(this::toReceptionResponse)
                .toList();
    }

    public Customer getRequiredCustomer(UUID customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found."));
    }

    private ReceptionCustomerResponse toReceptionResponse(Customer customer) {
        return new ReceptionCustomerResponse(
                customer.getId(),
                customer.getCustomerCode(),
                customer.getFullName(),
                customer.getPhone(),
                customer.getNationality(),
                customer.getStatus()
        );
    }
}
