package com.casino.casinoerp;

import com.casino.casinoerp.config.JwtAuthenticationFilter;
import com.casino.casinoerp.config.SecurityConfig;
import com.casino.casinoerp.controller.RunningFundsReportController;
import com.casino.casinoerp.dto.RunningFundsReportResponse;
import com.casino.casinoerp.service.JwtService;
import com.casino.casinoerp.service.RunningFundsReportService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RunningFundsReportController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class RunningFundsReportSecurityTests {
    @Autowired MockMvc mockMvc;
    @MockitoBean RunningFundsReportService service;
    @MockitoBean JwtService jwtService;

    @ParameterizedTest
    @ValueSource(strings = {"DIRECTOR", "SUPER_ADMIN"})
    void managementRolesCanReadReport(String role) throws Exception {
        when(service.getReport(any())).thenReturn(report());
        mockMvc.perform(get("/api/reports/running-funds?businessDate=2026-08-08")
                        .with(user(role.toLowerCase()).roles(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.businessDate").value("2026-08-08"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CASHIER", "RECEPTIONIST", "PIT_SUPERVISOR", "DEALER"})
    void operationalRolesCannotReadReport(String role) throws Exception {
        mockMvc.perform(get("/api/reports/running-funds?businessDate=2026-08-08")
                        .with(user(role.toLowerCase()).roles(role)))
                .andExpect(status().isForbidden());
    }

    private RunningFundsReportResponse report() {
        return new RunningFundsReportResponse(LocalDate.of(2026, 8, 8), "OPEN", LocalDateTime.now(),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                0, 0, 0, BigDecimal.ZERO, List.of());
    }
}
