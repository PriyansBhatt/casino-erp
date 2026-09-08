package com.casino.casinoerp;

import com.casino.casinoerp.config.JwtAuthenticationFilter;
import com.casino.casinoerp.config.SecurityConfig;
import com.casino.casinoerp.controller.BusinessDateContinuationOverrideController;
import com.casino.casinoerp.dto.BusinessDateContinuationOverrideResponse;
import com.casino.casinoerp.service.BusinessDateContinuationOverrideService;
import com.casino.casinoerp.service.JwtService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BusinessDateContinuationOverrideController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class BusinessDateContinuationOverrideSecurityTests {
    @Autowired MockMvc mockMvc;
    @MockitoBean BusinessDateContinuationOverrideService service;
    @MockitoBean JwtService jwtService;

    @ParameterizedTest
    @ValueSource(strings = {"DIRECTOR", "SUPER_ADMIN"})
    void managersCanCreateAndRevoke(String role) throws Exception {
        when(service.create(any())).thenReturn(response(true));
        when(service.revoke(any())).thenReturn(response(false));
        mockMvc.perform(post("/api/business-date/continuation-override")
                        .with(user(role.toLowerCase()).roles(role))
                        .contentType("application/json")
                        .content("{\"reason\":\"Emergency continuation\",\"durationMinutes\":30}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/business-date/continuation-override")
                        .with(user(role.toLowerCase()).roles(role))
                        .contentType("application/json")
                        .content("{\"reason\":\"Operations complete\"}"))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"RECEPTIONIST", "CASHIER", "DEALER", "PIT_SUPERVISOR"})
    void ordinaryRolesCannotCreateOrRevoke(String role) throws Exception {
        mockMvc.perform(post("/api/business-date/continuation-override")
                        .with(user(role.toLowerCase()).roles(role))
                        .contentType("application/json")
                        .content("{\"reason\":\"No authority\",\"durationMinutes\":30}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/business-date/continuation-override")
                        .with(user(role.toLowerCase()).roles(role))
                        .contentType("application/json")
                        .content("{\"reason\":\"No authority\"}"))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"RECEPTIONIST", "CASHIER", "DEALER", "PIT_SUPERVISOR", "DIRECTOR", "SUPER_ADMIN"})
    void authenticatedOperationalRolesCanRead(String role) throws Exception {
        when(service.current()).thenReturn(Optional.of(response(true)));
        mockMvc.perform(get("/api/business-date/continuation-override")
                        .with(user(role.toLowerCase()).roles(role)))
                .andExpect(status().isOk());
    }

    private BusinessDateContinuationOverrideResponse response(boolean active) {
        return new BusinessDateContinuationOverrideResponse(UUID.randomUUID(), LocalDate.of(2026, 9, 2),
                "Emergency continuation", null, Instant.parse("2026-09-09T07:00:00Z"),
                Instant.parse("2026-09-09T07:30:00Z"), active, null, null, null);
    }
}
