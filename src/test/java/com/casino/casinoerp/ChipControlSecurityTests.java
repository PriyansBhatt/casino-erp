package com.casino.casinoerp;

import com.casino.casinoerp.config.JwtAuthenticationFilter;
import com.casino.casinoerp.config.SecurityConfig;
import com.casino.casinoerp.controller.ChipControlController;
import com.casino.casinoerp.service.ChipControlService;
import com.casino.casinoerp.service.JwtService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.casino.casinoerp.dto.ChipControlDirectoryResponse;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChipControlController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class ChipControlSecurityTests {
    @Autowired MockMvc mockMvc;
    @MockitoBean ChipControlService service;
    @MockitoBean JwtService jwtService;

    @ParameterizedTest
    @ValueSource(strings = {"CASHIER", "PIT_SUPERVISOR", "DIRECTOR", "SUPER_ADMIN"})
    void approvedRolesCanReadChipControl(String role) throws Exception {
        when(service.getCurrentOpenSessionPositions()).thenReturn(
                new ChipControlDirectoryResponse(LocalDate.of(2026, 8, 8), List.of()));
        mockMvc.perform(get("/api/chip-control/sessions").with(user(role.toLowerCase()).roles(role)))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"RECEPTIONIST", "DEALER", "ACCOUNTS"})
    void unrelatedRolesCannotReadChipControl(String role) throws Exception {
        mockMvc.perform(get("/api/chip-control/sessions").with(user(role.toLowerCase()).roles(role)))
                .andExpect(status().isForbidden());
    }

    @org.junit.jupiter.api.Test void unauthenticatedRequestIsDenied() throws Exception {
        mockMvc.perform(get("/api/chip-control/sessions")).andExpect(status().isForbidden());
    }
}
