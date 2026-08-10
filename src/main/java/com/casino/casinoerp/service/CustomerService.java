package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.CustomerRegistrationRequest;
import com.casino.casinoerp.dto.PrivilegedCustomerResponse;
import com.casino.casinoerp.dto.ReceptionCustomerResponse;
import com.casino.casinoerp.entity.Customer;
import com.casino.casinoerp.entity.CustomerCategory;
import com.casino.casinoerp.entity.CustomerStatus;
import com.casino.casinoerp.entity.KycStatus;
import com.casino.casinoerp.exception.ResourceNotFoundException;
import com.casino.casinoerp.exception.ResourceConflictException;
import com.casino.casinoerp.repository.CustomerRepository;
import com.casino.casinoerp.repository.CustomerSessionRepository;
import com.casino.casinoerp.repository.CustomerVisitSummaryProjection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CustomerService {

    private static final int INITIAL_CUSTOMER_CODE_NUMBER = 1000;

    private final CustomerRepository customerRepository;
    private final CustomerSessionRepository customerSessionRepository;

    public CustomerService(
            CustomerRepository customerRepository,
            CustomerSessionRepository customerSessionRepository) {
        this.customerRepository = customerRepository;
        this.customerSessionRepository = customerSessionRepository;
    }
    public ReceptionCustomerResponse getCustomerByCode(String customerCode) {
        Customer customer = customerRepository
                .findByCustomerCode(customerCode)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found."));

        return toReceptionResponse(customer, getVisitSummaries(List.of(customer.getId())));
    }

    public List<ReceptionCustomerResponse> getAllCustomers() {
        List<Customer> customers = customerRepository.findAll();
        Map<UUID, CustomerVisitSummaryProjection> summaries = getVisitSummaries(
                customers.stream().map(Customer::getId).toList()
        );

        return customers
                .stream()
                .map(customer -> toReceptionResponse(customer, summaries))
                .toList();
    }

    public List<ReceptionCustomerResponse> searchCustomers(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Search query is required.");
        }

        String normalizedQuery = query.trim();
        List<Customer> customers = customerRepository
                .findByCustomerCodeContainingIgnoreCaseOrFullNameContainingIgnoreCaseOrPhoneContainingIgnoreCase(
                        normalizedQuery,
                        normalizedQuery,
                        normalizedQuery
                );
        Map<UUID, CustomerVisitSummaryProjection> summaries = getVisitSummaries(
                customers.stream().map(Customer::getId).toList()
        );

        return customers
                .stream()
                .map(customer -> toReceptionResponse(customer, summaries))
                .toList();
    }

    public Customer getRequiredCustomer(UUID customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found."));
    }

    public PrivilegedCustomerResponse getPrivilegedCustomerById(UUID customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found."));

        return toPrivilegedResponse(customer, getVisitSummaries(List.of(customerId)));
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
        customer.setStatus(CustomerStatus.ACTIVE);
        customer.setCategory(CustomerCategory.NORMAL);
        customer.setKycStatus(KycStatus.PENDING);

        return toReceptionResponse(customerRepository.save(customer), Map.of());
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

    private Map<UUID, CustomerVisitSummaryProjection> getVisitSummaries(Collection<UUID> customerIds) {
        if (customerIds.isEmpty()) {
            return Map.of();
        }

        return customerSessionRepository.findVisitSummariesByCustomerIds(customerIds)
                .stream()
                .collect(Collectors.toMap(
                        CustomerVisitSummaryProjection::getCustomerId,
                        Function.identity()
                ));
    }

    private ReceptionCustomerResponse toReceptionResponse(
            Customer customer,
            Map<UUID, CustomerVisitSummaryProjection> summaries) {
        CustomerVisitSummaryProjection summary = summaries.get(customer.getId());

        return new ReceptionCustomerResponse(
                customer.getId(),
                customer.getCustomerCode(),
                customer.getFullName(),
                customer.getPhone(),
                customer.getNationality(),
                customer.getStatus() == null ? null : customer.getStatus().name(),
                summary == null ? 0 : summary.getTotalVisits(),
                summary == null ? null : summary.getLastVisitBusinessDate(),
                summary == null ? null : summary.getLastEntryTime(),
                summary != null && summary.getHasActiveSession(),
                summary == null ? null : summary.getActiveSessionId()
        );
    }

    private PrivilegedCustomerResponse toPrivilegedResponse(
            Customer customer,
            Map<UUID, CustomerVisitSummaryProjection> summaries) {
        CustomerVisitSummaryProjection summary = summaries.get(customer.getId());

        return new PrivilegedCustomerResponse(
                customer.getId(),
                customer.getCustomerCode(),
                customer.getFullName(),
                customer.getPhone(),
                customer.getNationality(),
                customer.getStatus() == null ? null : customer.getStatus().name(),
                summary == null ? 0 : summary.getTotalVisits(),
                summary == null ? null : summary.getLastVisitBusinessDate(),
                summary == null ? null : summary.getLastEntryTime(),
                summary != null && summary.getHasActiveSession(),
                summary == null ? null : summary.getActiveSessionId()
        );
    }
}
