package com.casino.casinoerp;

import com.casino.casinoerp.config.JwtAuthenticationFilter;
import com.casino.casinoerp.config.SecurityConfig;
import com.casino.casinoerp.controller.CustomerController;
import com.casino.casinoerp.dto.PrivilegedCustomerResponse;
import com.casino.casinoerp.exception.ResourceNotFoundException;
import com.casino.casinoerp.service.CustomerService;
import com.casino.casinoerp.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@WebMvcTest(CustomerController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class ReceptionCustomerSecurityTests {

    private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerService customerService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void receptionistCanListCustomers() throws Exception {
        when(customerService.getAllCustomers()).thenReturn(List.of());

        mockMvc.perform(get("/api/customers").with(user("reception").roles("RECEPTIONIST")))
                .andExpect(status().isOk());
    }

    @Test
    void receptionistCanSearchCustomers() throws Exception {
        when(customerService.searchCustomers("CUS-1001")).thenReturn(List.of());

        mockMvc.perform(get("/api/customers/search")
                        .param("query", "CUS-1001")
                        .with(user("reception").roles("RECEPTIONIST")))
                .andExpect(status().isOk());
    }

    @Test
    void receptionistCanGetCustomerByCode() throws Exception {
        mockMvc.perform(get("/api/customers/CUS-1001")
                        .with(user("reception").roles("RECEPTIONIST")))
                .andExpect(status().isOk());
    }

    @Test
    void receptionistCannotAccessPrivilegedCustomerDetail() throws Exception {
        mockMvc.perform(get("/api/customers/id/{customerId}", CUSTOMER_ID)
                        .with(user("reception").roles("RECEPTIONIST")))
                .andExpect(status().isForbidden());
    }

    @Test
    void directorCanAccessPrivilegedCustomerDetail() throws Exception {
        when(customerService.getPrivilegedCustomerById(CUSTOMER_ID)).thenReturn(privilegedCustomer());

        mockMvc.perform(get("/api/customers/id/{customerId}", CUSTOMER_ID)
                        .with(user("director").roles("DIRECTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CUSTOMER_ID.toString()))
                .andExpect(jsonPath("$.customerCode").value("CUS-1001"));
    }

    @Test
    void superAdminCanAccessPrivilegedCustomerDetail() throws Exception {
        when(customerService.getPrivilegedCustomerById(CUSTOMER_ID)).thenReturn(privilegedCustomer());

        mockMvc.perform(get("/api/customers/id/{customerId}", CUSTOMER_ID)
                        .with(user("superadmin").roles("SUPER_ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedUserCannotAccessProtectedCustomerEndpoint() throws Exception {
        mockMvc.perform(get("/api/customers"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownCustomerIdReturnsControlledNotFound() throws Exception {
        when(customerService.getPrivilegedCustomerById(CUSTOMER_ID))
                .thenThrow(new ResourceNotFoundException("Customer not found."));

        mockMvc.perform(get("/api/customers/id/{customerId}", CUSTOMER_ID)
                        .with(user("director").roles("DIRECTOR")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Customer not found."));
    }

    @Test
    void unrelatedAuthenticatedRoleCannotListCustomers() throws Exception {
        mockMvc.perform(get("/api/customers").with(user("cashier").roles("CASHIER")))
                .andExpect(status().isForbidden());
    }

    private PrivilegedCustomerResponse privilegedCustomer() {
        return new PrivilegedCustomerResponse(
                CUSTOMER_ID,
                "CUS-1001",
                "Rina Rai",
                "+9779800000000",
                "Nepali",
                "ACTIVE"
        );
    }
}
