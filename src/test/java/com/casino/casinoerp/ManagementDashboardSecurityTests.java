package com.casino.casinoerp;

import com.casino.casinoerp.config.*;
import com.casino.casinoerp.controller.ManagementDashboardController;
import com.casino.casinoerp.dto.ManagementDashboardResponse;
import com.casino.casinoerp.dto.ManagementDashboardResponse.Metric;
import com.casino.casinoerp.exception.ResourceNotFoundException;
import com.casino.casinoerp.security.Role;
import com.casino.casinoerp.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.*;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ManagementDashboardController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class ManagementDashboardSecurityTests {
    @Autowired MockMvc mvc;
    @MockitoBean ManagementDashboardService service;
    @MockitoBean JwtService jwtService;
    private final LocalDate date = LocalDate.of(2026, 9, 1);

    @ParameterizedTest @ValueSource(strings = {"DIRECTOR", "SUPER_ADMIN"})
    void managementCanReadNormalApiResponse(String role) throws Exception {
        when(service.getDashboard(date)).thenReturn(new ManagementDashboardResponse(date, "CLOSED",
                OffsetDateTime.parse("2026-09-02T10:00:00+05:45"), "Asia/Kathmandu",
                date.atTime(9, 0).atOffset(ZoneOffset.ofHoursMinutes(5, 45)),
                date.plusDays(1).atTime(9, 0).atOffset(ZoneOffset.ofHoursMinutes(5, 45)),
                Map.of("buyInTotal", Metric.available(BigDecimal.ZERO, "Posted receipts"),
                        "activeMachines", Metric.unavailable("Not implemented"))));
        mvc.perform(get("/api/dashboard/management").param("businessDate", date.toString()).with(user(role).roles(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.data.businessDate").value(date.toString()))
                .andExpect(jsonPath("$.data.businessDateStatus").value("CLOSED"))
                .andExpect(jsonPath("$.data.summary.buyInTotal.value").value(0))
                .andExpect(jsonPath("$.data.summary.activeMachines.available").value(false))
                .andExpect(jsonPath("$.data.summary.activeMachines.value").doesNotExist());
        verify(service).getDashboard(date);
    }

    @ParameterizedTest @EnumSource(value = Role.class, mode = EnumSource.Mode.EXCLUDE, names = {"DIRECTOR", "SUPER_ADMIN"})
    void everyOtherRoleIsForbidden(Role role) throws Exception {
        mvc.perform(get("/api/dashboard/management").with(user("operator").roles(role.name())))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void anonymousIsRejected() throws Exception {
        mvc.perform(get("/api/dashboard/management")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void omittedDateDelegatesToLifecycleSelection() throws Exception {
        mvc.perform(get("/api/dashboard/management").with(user("director").roles("DIRECTOR")))
                .andExpect(status().isOk());
        verify(service).getDashboard(null);
    }

    @Test void unknownDateReturns404() throws Exception {
        when(service.getDashboard(date)).thenThrow(new ResourceNotFoundException("Business Date not found."));
        mvc.perform(get("/api/dashboard/management").param("businessDate", date.toString()).with(user("director").roles("DIRECTOR")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false));
    }

    @Test void malformedDateIsRejected() throws Exception {
        mvc.perform(get("/api/dashboard/management").param("businessDate", "invalid").with(user("director").roles("DIRECTOR")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
