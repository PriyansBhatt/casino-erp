package com.casino.casinoerp;

import com.casino.casinoerp.config.JwtAuthenticationFilter;
import com.casino.casinoerp.config.SecurityConfig;
import com.casino.casinoerp.controller.StaffAttendanceController;
import com.casino.casinoerp.service.JwtService;
import com.casino.casinoerp.service.StaffAttendanceService;
import com.casino.casinoerp.service.StaffAttendanceCorrectionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StaffAttendanceController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class StaffAttendanceSecurityTests {
    @Autowired MockMvc mvc;
    @MockitoBean StaffAttendanceService service;
    @MockitoBean StaffAttendanceCorrectionService correctionService;
    @MockitoBean JwtService jwt;

    @ParameterizedTest
    @ValueSource(strings = {"CASHIER", "RECEPTIONIST", "PIT_SUPERVISOR", "DEALER", "DIRECTOR", "SUPER_ADMIN"})
    void authenticatedActiveRoleCanUseSelfServiceEndpoints(String role) throws Exception {
        mvc.perform(post("/api/attendance/check-in").with(user("employee").roles(role)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/attendance/check-out").with(user("employee").roles(role)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/attendance/current").with(user("employee").roles(role)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/attendance/me").with(user("employee").roles(role)))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DIRECTOR", "SUPER_ADMIN"})
    void managementCanReadBusinessDateReport(String role) throws Exception {
        mvc.perform(get("/api/attendance").param("businessDate", "2026-09-07")
                        .with(user("manager").roles(role)))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CASHIER", "RECEPTIONIST", "PIT_SUPERVISOR", "DEALER"})
    void operationalRolesCannotReadAllEmployeeReport(String role) throws Exception {
        mvc.perform(get("/api/attendance").param("businessDate", "2026-09-07")
                        .with(user("employee").roles(role)))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DIRECTOR", "SUPER_ADMIN"})
    void managementCanCreateAndReadCorrections(String role) throws Exception {
        String id = "00000000-0000-0000-0000-000000000001";
        mvc.perform(post("/api/attendance/{id}/corrections", id)
                        .contentType("application/json")
                        .content("""
                                {"type":"MISSED_CHECKOUT","checkOutAt":"2026-09-11T03:30:00Z","reason":"Forgot checkout"}
                                """)
                        .with(user("manager").roles(role)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/attendance/{id}/corrections", id)
                        .with(user("manager").roles(role)))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CASHIER", "RECEPTIONIST", "PIT_SUPERVISOR", "DEALER"})
    void operationalRolesCannotCreateOrReadCorrections(String role) throws Exception {
        String id = "00000000-0000-0000-0000-000000000001";
        mvc.perform(post("/api/attendance/{id}/corrections", id)
                        .contentType("application/json")
                        .content("""
                                {"type":"MISSED_CHECKOUT","checkOutAt":"2026-09-11T03:30:00Z","reason":"Forgot checkout"}
                                """)
                        .with(user("employee").roles(role)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/attendance/{id}/corrections", id)
                        .with(user("employee").roles(role)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedAttendanceAccessIsDenied() throws Exception {
        mvc.perform(post("/api/attendance/check-in")).andExpect(status().isForbidden());
        mvc.perform(get("/api/attendance/me")).andExpect(status().isForbidden());
    }
}
