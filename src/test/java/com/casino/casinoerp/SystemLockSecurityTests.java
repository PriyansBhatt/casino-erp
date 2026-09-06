package com.casino.casinoerp;

import com.casino.casinoerp.config.JwtAuthenticationFilter;
import com.casino.casinoerp.config.SecurityConfig;
import com.casino.casinoerp.controller.SystemLockController;
import com.casino.casinoerp.service.BusinessDateService;
import com.casino.casinoerp.service.JwtService;
import com.casino.casinoerp.service.SystemLockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SystemLockController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class SystemLockSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SystemLockService systemLockService;

    @MockitoBean
    private BusinessDateService businessDateService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void superAdminCanReadSystemLockStatus() throws Exception {
        when(systemLockService.getLockStart()).thenReturn(LocalTime.of(6, 30));
        when(systemLockService.getLockEnd()).thenReturn(LocalTime.of(12, 30));
        when(businessDateService.getCurrentBusinessDate()).thenReturn(LocalDate.of(2026, 8, 8));

        mockMvc.perform(get("/api/system-lock")
                        .with(user("superadmin").roles("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.businessDate").value("2026-08-08"));
    }

    @Test
    void unsupportedRolesAndUnauthenticatedUsersCannotReadSystemLockStatus() throws Exception {
        mockMvc.perform(get("/api/system-lock"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/system-lock")
                        .with(user("director").roles("DIRECTOR")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/system-lock")
                        .with(user("cashier").roles("CASHIER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void systemLockManagementRemainsSuperAdminOnly() throws Exception {
        mockMvc.perform(put("/api/system-lock/lock")
                        .with(user("superadmin").roles("SUPER_ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/system-lock/unlock")
                        .with(user("director").roles("DIRECTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Operational verification\"}"))
                .andExpect(status().isForbidden());
    }
}
