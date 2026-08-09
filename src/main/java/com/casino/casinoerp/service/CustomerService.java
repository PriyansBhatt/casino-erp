package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.CustomerRegistrationRequest;
import com.casino.casinoerp.dto.PrivilegedCustomerResponse;
import com.casino.casinoerp.dto.ReceptionCustomerResponse;
import com.casino.casinoerp.entity.Customer;
import com.casino.casinoerp.exception.ResourceNotFoundException;
import com.casino.casinoerp.exception.ResourceConflictException;
import com.casino.casinoerp.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CustomerService {

    private static final int INITIAL_CUSTOMER_CODE_NUMBER = 1000;

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

    public PrivilegedCustomerResponse getPrivilegedCustomerById(UUID customerId) {
        return customerRepository.findById(customerId)
                .map(this::toPrivilegedResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found."));
    }

    @Transactional
    public synchronized ReceptionCustomerResponse registerCustomer(CustomerRegistrationRequest request) {
        String normalizedPhone = normalizePhone(request.phone());

        if (customerRepository.existsByNormalizedPhone(normalizedPhone)) {
            throw new ResourceConflictException("Customer with this phone already exists.");
        }

        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setCustomerCode(generateCustomerCode());
        customer.setFullName(normalizeText(request.fullName()));
        customer.setPhone(normalizedPhone);
        customer.setNationality(normalizeText(request.nationality()));
        customer.setStatus("ACTIVE");

        return toReceptionResponse(customerRepository.save(customer));
    }

    private String generateCustomerCode() {
        Integer maximumCodeNumber = customerRepository.findMaximumCustomerCodeNumber();
        int candidateNumber = maximumCodeNumber == null
                ? INITIAL_CUSTOMER_CODE_NUMBER + 1
                : maximumCodeNumber + 1;
        String candidate = "CUS-" + candidateNumber;

        while (customerRepository.existsByCustomerCode(candidate)) {
            candidateNumber++;
            candidate = "CUS-" + candidateNumber;
        }

        return candidate;
    }

    private String normalizeText(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    private String normalizePhone(String value) {
        return value.trim().replaceAll("[\\s-]+", "");
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

    private PrivilegedCustomerResponse toPrivilegedResponse(Customer customer) {
        return new PrivilegedCustomerResponse(
                customer.getId(),
                customer.getCustomerCode(),
                customer.getFullName(),
                customer.getPhone(),
                customer.getNationality(),
                customer.getStatus()
        );
    }
}
