package com.casino.casinoerp;

import com.casino.casinoerp.dto.CustomerRegistrationRequest;
import com.casino.casinoerp.dto.PrivilegedCustomerResponse;
import com.casino.casinoerp.dto.ReceptionCustomerResponse;
import com.casino.casinoerp.entity.Customer;
import com.casino.casinoerp.exception.ResourceNotFoundException;
import com.casino.casinoerp.exception.ResourceConflictException;
import com.casino.casinoerp.repository.CustomerRepository;
import com.casino.casinoerp.service.CustomerService;
import org.junit.jupiter.api.Test;

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
    private final CustomerService service = new CustomerService(repository);

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
                "ACTIVE"
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
                "ACTIVE"
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
                "ACTIVE"
        ));
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
}
