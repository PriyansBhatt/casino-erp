package com.casino.casinoerp;

import com.casino.casinoerp.config.JwtAuthenticationFilter;
import com.casino.casinoerp.config.SecurityConfig;
import com.casino.casinoerp.controller.BusinessStatusController;
import com.casino.casinoerp.dto.OperationalStatusResponse;
import com.casino.casinoerp.entity.BusinessDateHealth;
import com.casino.casinoerp.service.JwtService;
import com.casino.casinoerp.service.OperationalStatusService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BusinessStatusController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class BusinessStatusSecurityTests {
    @Autowired MockMvc mockMvc;
    @MockitoBean OperationalStatusService service;
    @MockitoBean JwtService jwtService;

    @ParameterizedTest
    @ValueSource(strings = {"RECEPTIONIST", "CASHIER", "DEALER", "PIT_SUPERVISOR", "DIRECTOR", "SUPER_ADMIN"})
    void operationalRolesCanReadStatus(String role) throws Exception {
        when(service.current()).thenReturn(new OperationalStatusResponse(LocalDate.of(2026, 9, 2),
                LocalDate.of(2026, 9, 7), true, BusinessDateHealth.STALE, true, 5,
                "The OPEN Business Date is earlier than the expected Business Date.",
                false, null, LocalDateTime.of(2026, 9, 4, 12, 0)));
        mockMvc.perform(get("/api/business-status/current").with(user(role.toLowerCase()).roles(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.businessDate").value("2026-09-02"))
                .andExpect(jsonPath("$.data.businessDateHealth").value("STALE"))
                .andExpect(jsonPath("$.data.systemLocked").value(false));
    }
}
