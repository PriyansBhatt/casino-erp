package com.casino.casinoerp;

import com.casino.casinoerp.dto.ReceptionCustomerResponse;
import com.casino.casinoerp.entity.Customer;
import com.casino.casinoerp.exception.ResourceNotFoundException;
import com.casino.casinoerp.repository.CustomerRepository;
import com.casino.casinoerp.service.CustomerService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
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
