package com.casino.casinoerp;

import com.casino.casinoerp.config.JwtAuthenticationFilter;
import com.casino.casinoerp.config.SecurityConfig;
import com.casino.casinoerp.controller.CustomerSessionController;
import com.casino.casinoerp.dto.ReceptionSessionResponse;
import com.casino.casinoerp.service.CustomerSessionService;
import com.casino.casinoerp.service.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerSessionController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class CustomerSessionSecurityTests {

    private static final UUID CUSTOMER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SESSION_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerSessionService customerSessionService;

    @MockitoBean
    private JwtService jwtService;

    @ParameterizedTest
    @ValueSource(strings = {"CASHIER", "PIT_SUPERVISOR", "DEALER", "RECEPTIONIST", "DIRECTOR", "SUPER_ADMIN"})
    void authorizedRolesCanReadActiveCustomerSession(String role) throws Exception {
        when(customerSessionService.getActiveSession(CUSTOMER_ID)).thenReturn(activeSession());

        mockMvc.perform(get("/api/sessions/active/customer/{customerId}", CUSTOMER_ID)
                        .with(user(role.toLowerCase()).roles(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SESSION_ID.toString()))
                .andExpect(jsonPath("$.sessionCode").value("SES-2026-08-08-001"))
                .andExpect(jsonPath("$.businessDate").value("2026-08-08"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.riskLevel").doesNotExist())
                .andExpect(jsonPath("$.internalNotes").doesNotExist());
    }

    @Test
    void cashierCannotListAllSessions() throws Exception {
        mockMvc.perform(get("/api/sessions")
                        .with(user("cashier").roles("CASHIER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void cashierCannotOpenOrCloseSessions() throws Exception {
        mockMvc.perform(post("/api/sessions")
                        .with(user("cashier").roles("CASHIER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"" + CUSTOMER_ID + "\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/sessions/{sessionId}/close", SESSION_ID)
                        .with(user("cashier").roles("CASHIER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedUserCannotReadActiveCustomerSession() throws Exception {
        mockMvc.perform(get("/api/sessions/active/customer/{customerId}", CUSTOMER_ID))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"RECEPTIONIST", "DIRECTOR", "SUPER_ADMIN"})
    void authorizedListRolesCanSelectExactBusinessDate(String role) throws Exception {
        LocalDate date = LocalDate.of(2026, 8, 8);
        when(customerSessionService.getAllReceptionSessions(date)).thenReturn(List.of(activeSession()));
        mockMvc.perform(get("/api/sessions").param("businessDate", date.toString())
                        .with(user(role).roles(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].businessDate").value(date.toString()));
        verify(customerSessionService).getAllReceptionSessions(date);
    }

    @Test
    void emptyFilteredDateReturnsEmptyList() throws Exception {
        LocalDate date = LocalDate.of(2026, 9, 5);
        when(customerSessionService.getAllReceptionSessions(date)).thenReturn(List.of());
        mockMvc.perform(get("/api/sessions").param("businessDate", date.toString())
                        .with(user("reception").roles("RECEPTIONIST")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        verify(customerSessionService).getAllReceptionSessions(date);
    }

    @Test
    void unfilteredListRemainsCompatible() throws Exception {
        when(customerSessionService.getAllReceptionSessions(null)).thenReturn(List.of(activeSession()));
        mockMvc.perform(get("/api/sessions").with(user("director").roles("DIRECTOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        verify(customerSessionService).getAllReceptionSessions(null);
    }

    @Test
    void invalidDateIsRejected() throws Exception {
        mockMvc.perform(get("/api/sessions").param("businessDate", "2026-09-32")
                        .with(user("reception").roles("RECEPTIONIST")))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "CASHIER", "PIT_SUPERVISOR", "DEALER", "AUDITOR"})
    void filteredListDoesNotExpandAuthorization(String role) throws Exception {
        mockMvc.perform(get("/api/sessions").param("businessDate", "2026-09-02")
                        .with(user(role).roles(role)))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DIRECTOR", "ADMIN"})
    void viewOrFrontendRolesDoNotGainMutationPermission(String role) throws Exception {
        mockMvc.perform(post("/api/sessions").with(user(role).roles(role))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"" + CUSTOMER_ID + "\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/sessions/{sessionId}/close", SESSION_ID).with(user(role).roles(role)))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"RECEPTIONIST", "SUPER_ADMIN"})
    void authorizedMutationRolesRemainAllowed(String role) throws Exception {
        when(customerSessionService.openSession(CUSTOMER_ID)).thenReturn(activeSession());
        when(customerSessionService.closeSession(SESSION_ID)).thenReturn(activeSession());
        mockMvc.perform(post("/api/sessions").with(user(role).roles(role))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"" + CUSTOMER_ID + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/sessions/{sessionId}/close", SESSION_ID).with(user(role).roles(role)))
                .andExpect(status().isOk());
    }

    private ReceptionSessionResponse activeSession() {
        return new ReceptionSessionResponse(
                SESSION_ID,
                "SES-2026-08-08-001",
                CUSTOMER_ID,
                LocalDate.of(2026, 8, 8),
                LocalDateTime.of(2026, 8, 8, 21, 0),
                null,
                "OPEN",
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                null,
                LocalDate.of(2026, 8, 8),
                "Cashier verification session"
        );
    }
}
