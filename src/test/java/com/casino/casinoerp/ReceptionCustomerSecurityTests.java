package com.casino.casinoerp;

import com.casino.casinoerp.config.JwtAuthenticationFilter;
import com.casino.casinoerp.config.SecurityConfig;
import com.casino.casinoerp.controller.CustomerController;
import com.casino.casinoerp.dto.CustomerRegistrationRequest;
import com.casino.casinoerp.dto.PrivilegedCustomerResponse;
import com.casino.casinoerp.dto.ReceptionCustomerResponse;
import com.casino.casinoerp.exception.ResourceConflictException;
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
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.http.MediaType.APPLICATION_JSON;
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

    @Test
    void receptionistCanRegisterCustomer() throws Exception {
        stubRegistration();

        performValidRegistration("reception", "RECEPTIONIST")
                .andExpect(status().isCreated());
    }

    @Test
    void directorCanRegisterCustomer() throws Exception {
        stubRegistration();

        performValidRegistration("director", "DIRECTOR")
                .andExpect(status().isCreated());
    }

    @Test
    void superAdminCanRegisterCustomer() throws Exception {
        stubRegistration();

        performValidRegistration("superadmin", "SUPER_ADMIN")
                .andExpect(status().isCreated());
    }

    @Test
    void unauthenticatedUserCannotRegisterCustomer() throws Exception {
        mockMvc.perform(post("/api/customers")
                        .contentType(APPLICATION_JSON)
                        .content(validRegistrationJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidRegistrationRequestReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/customers")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"fullName":"", "phone":"invalid", "nationality":""}
                                """)
                        .with(user("reception").roles("RECEPTIONIST")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void duplicatePhoneReturnsConflict() throws Exception {
        when(customerService.registerCustomer(any(CustomerRegistrationRequest.class)))
                .thenThrow(new ResourceConflictException("Customer with this phone already exists."));

        performValidRegistration("reception", "RECEPTIONIST")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Customer with this phone already exists."));
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

    private void stubRegistration() {
        when(customerService.registerCustomer(any(CustomerRegistrationRequest.class)))
                .thenReturn(new ReceptionCustomerResponse(
                        CUSTOMER_ID,
                        "CUS-1001",
                        "Rina Rai",
                        "+9779800000001",
                        "Nepali",
                        "ACTIVE"
                ));
    }

    private org.springframework.test.web.servlet.ResultActions performValidRegistration(
            String username,
            String role) throws Exception {
        return mockMvc.perform(post("/api/customers")
                .contentType(APPLICATION_JSON)
                .content(validRegistrationJson())
                .with(user(username).roles(role)));
    }

    private String validRegistrationJson() {
        return """
                {"fullName":"Rina Rai", "phone":"+977-9800 000001", "nationality":"Nepali"}
                """;
    }
}
