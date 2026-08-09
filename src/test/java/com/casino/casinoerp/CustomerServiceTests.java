package com.casino.casinoerp;

import com.casino.casinoerp.dto.CustomerRegistrationRequest;
import com.casino.casinoerp.dto.PrivilegedCustomerResponse;
import com.casino.casinoerp.dto.ReceptionCustomerResponse;
import com.casino.casinoerp.entity.Customer;
import com.casino.casinoerp.exception.ResourceNotFoundException;
import com.casino.casinoerp.exception.ResourceConflictException;
import com.casino.casinoerp.repository.CustomerRepository;
import com.casino.casinoerp.repository.CustomerSessionRepository;
import com.casino.casinoerp.repository.CustomerVisitSummaryProjection;
import com.casino.casinoerp.service.CustomerService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerServiceTests {

    private final CustomerRepository repository = mock(CustomerRepository.class);
    private final CustomerSessionRepository sessionRepository = mock(CustomerSessionRepository.class);
    private final CustomerService service = new CustomerService(repository, sessionRepository);

    @Test
    void searchesRepositoryFieldsAndReturnsReceptionSafeData() {
        Customer customer = customer("CUS-1001", "Rina Rai", "+9779800000000", "ACTIVE");
        when(repository.findByCustomerCodeContainingIgnoreCaseOrFullNameContainingIgnoreCaseOrPhoneContainingIgnoreCase(
                "rina", "rina", "rina"
        )).thenReturn(List.of(customer));

        List<ReceptionCustomerResponse> result = service.searchCustomers("  rina  ");

        assertThat(result).containsExactly(new ReceptionCustomerResponse(
                customer.getId(),
                "CUS-1001",
                "Rina Rai",
                "+9779800000000",
                "Nepali",
                "ACTIVE",
                0,
                null,
                null,
                false,
                null
        ));
        verify(repository)
                .findByCustomerCodeContainingIgnoreCaseOrFullNameContainingIgnoreCaseOrPhoneContainingIgnoreCase(
                        "rina", "rina", "rina"
                );
    }

    @Test
    void missingCustomerCodeReturnsNotFound() {
        when(repository.findByCustomerCode("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCustomerByCode("MISSING"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Customer not found.");
    }

    @Test
    void findsCustomerByCodeAndReturnsReceptionSafeData() {
        Customer customer = customer("CUS-1001", "Rina Rai", "+9779800000000", "ACTIVE");
        when(repository.findByCustomerCode("CUS-1001")).thenReturn(Optional.of(customer));

        ReceptionCustomerResponse result = service.getCustomerByCode("CUS-1001");

        assertThat(result).isEqualTo(new ReceptionCustomerResponse(
                customer.getId(),
                "CUS-1001",
                "Rina Rai",
                "+9779800000000",
                "Nepali",
                "ACTIVE",
                0,
                null,
                null,
                false,
                null
        ));
    }

    @Test
    void findsCustomerByUuidAndMapsPrivilegedDetail() {
        Customer customer = customer("CUS-1001", "Rina Rai", "+9779800000000", "ACTIVE");
        when(repository.findById(customer.getId())).thenReturn(Optional.of(customer));

        PrivilegedCustomerResponse result = service.getPrivilegedCustomerById(customer.getId());

        assertThat(result).isEqualTo(new PrivilegedCustomerResponse(
                customer.getId(),
                "CUS-1001",
                "Rina Rai",
                "+9779800000000",
                "Nepali",
                "ACTIVE",
                0,
                null,
                null,
                false,
                null
        ));
    }

    @Test
    void customerWithNoSessionsHasEmptyVisitSummary() {
        Customer customer = customer("CUS-1001", "Rina Rai", "+9779800000000", "ACTIVE");
        when(repository.findAll()).thenReturn(List.of(customer));
        when(sessionRepository.findVisitSummariesByCustomerIds(List.of(customer.getId())))
                .thenReturn(List.of());

        ReceptionCustomerResponse result = service.getAllCustomers().getFirst();

        assertThat(result.totalVisits()).isZero();
        assertThat(result.lastVisitBusinessDate()).isNull();
        assertThat(result.lastEntryTime()).isNull();
        assertThat(result.hasActiveSession()).isFalse();
        assertThat(result.activeSessionId()).isNull();
    }

    @Test
    void mapsOneClosedSessionVisitSummary() {
        Customer customer = customer("CUS-1001", "Rina Rai", "+9779800000000", "ACTIVE");
        LocalDate businessDate = LocalDate.of(2026, 8, 7);
        LocalDateTime entryTime = LocalDateTime.of(2026, 8, 7, 20, 15);
        CustomerVisitSummaryProjection visitSummary =
                summary(customer.getId(), 1, businessDate, entryTime, false, null);
        when(repository.findAll()).thenReturn(List.of(customer));
        when(sessionRepository.findVisitSummariesByCustomerIds(List.of(customer.getId())))
                .thenReturn(List.of(visitSummary));

        ReceptionCustomerResponse result = service.getAllCustomers().getFirst();

        assertThat(result.totalVisits()).isEqualTo(1);
        assertThat(result.lastVisitBusinessDate()).isEqualTo(businessDate);
        assertThat(result.lastEntryTime()).isEqualTo(entryTime);
        assertThat(result.hasActiveSession()).isFalse();
        assertThat(result.activeSessionId()).isNull();
    }

    @Test
    void mapsMultipleSessionsLatestVisitAndActiveSession() {
        Customer customer = customer("CUS-1001", "Rina Rai", "+9779800000000", "ACTIVE");
        UUID activeSessionId = UUID.randomUUID();
        LocalDate latestBusinessDate = LocalDate.of(2026, 8, 8);
        LocalDateTime latestEntryTime = LocalDateTime.of(2026, 8, 8, 22, 30);
        CustomerVisitSummaryProjection visitSummary = summary(
                customer.getId(),
                3,
                latestBusinessDate,
                latestEntryTime,
                true,
                activeSessionId
        );
        when(repository.findAll()).thenReturn(List.of(customer));
        when(sessionRepository.findVisitSummariesByCustomerIds(List.of(customer.getId())))
                .thenReturn(List.of(visitSummary));

        ReceptionCustomerResponse result = service.getAllCustomers().getFirst();

        assertThat(result.totalVisits()).isEqualTo(3);
        assertThat(result.lastVisitBusinessDate()).isEqualTo(latestBusinessDate);
        assertThat(result.lastEntryTime()).isEqualTo(latestEntryTime);
        assertThat(result.hasActiveSession()).isTrue();
        assertThat(result.activeSessionId()).isEqualTo(activeSessionId);
    }

    @Test
    void missingCustomerUuidReturnsNotFound() {
        UUID customerId = UUID.randomUUID();
        when(repository.findById(customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPrivilegedCustomerById(customerId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Customer not found.");
    }

    @Test
    void registersCustomerWithServerGeneratedValuesAndNormalizedInput() {
        when(repository.existsByNormalizedPhone("+9779800000001")).thenReturn(false);
        when(repository.findMaximumCustomerCodeNumber()).thenReturn(1000);
        when(repository.existsByCustomerCode("CUS-1001")).thenReturn(false);
        when(repository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReceptionCustomerResponse result = service.registerCustomer(new CustomerRegistrationRequest(
                "  Rina   Rai  ",
                "+977-9800 000001",
                "  New   Zealand  "
        ));

        assertThat(result.id()).isNotNull();
        assertThat(result.customerCode()).isEqualTo("CUS-1001");
        assertThat(result.fullName()).isEqualTo("Rina Rai");
        assertThat(result.phone()).isEqualTo("+9779800000001");
        assertThat(result.nationality()).isEqualTo("New Zealand");
        assertThat(result.status()).isEqualTo("ACTIVE");
    }

    @Test
    void rejectsDuplicateNormalizedPhone() {
        when(repository.existsByNormalizedPhone("+9779800000001")).thenReturn(true);

        assertThatThrownBy(() -> service.registerCustomer(new CustomerRegistrationRequest(
                "Rina Rai",
                "+977-9800 000001",
                "Nepali"
        )))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessage("Customer with this phone already exists.");
    }

    @Test
    void skipsCollidingGeneratedCustomerCode() {
        when(repository.existsByNormalizedPhone("+9779800000001")).thenReturn(false);
        when(repository.findMaximumCustomerCodeNumber()).thenReturn(1000);
        when(repository.existsByCustomerCode("CUS-1001")).thenReturn(true);
        when(repository.existsByCustomerCode("CUS-1002")).thenReturn(false);
        when(repository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReceptionCustomerResponse result = service.registerCustomer(new CustomerRegistrationRequest(
                "Rina Rai",
                "+9779800000001",
                "Nepali"
        ));

        assertThat(result.customerCode()).isEqualTo("CUS-1002");
    }

    private Customer customer(String code, String name, String phone, String status) {
        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setCustomerCode(code);
        customer.setFullName(name);
        customer.setPhone(phone);
        customer.setNationality("Nepali");
        customer.setStatus(status);
        return customer;
    }

    private CustomerVisitSummaryProjection summary(
            UUID customerId,
            long totalVisits,
            LocalDate lastVisitBusinessDate,
            LocalDateTime lastEntryTime,
            boolean hasActiveSession,
            UUID activeSessionId) {
        CustomerVisitSummaryProjection summary = mock(CustomerVisitSummaryProjection.class);
        when(summary.getCustomerId()).thenReturn(customerId);
        when(summary.getTotalVisits()).thenReturn(totalVisits);
        when(summary.getLastVisitBusinessDate()).thenReturn(lastVisitBusinessDate);
        when(summary.getLastEntryTime()).thenReturn(lastEntryTime);
        when(summary.getHasActiveSession()).thenReturn(hasActiveSession);
        when(summary.getActiveSessionId()).thenReturn(activeSessionId);
        return summary;
    }
}
