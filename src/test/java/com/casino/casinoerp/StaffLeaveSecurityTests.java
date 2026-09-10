package com.casino.casinoerp;

import com.casino.casinoerp.config.*;
import com.casino.casinoerp.controller.*;
import com.casino.casinoerp.service.*;
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

@WebMvcTest({LeaveTypeController.class, StaffLeaveRequestController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class StaffLeaveSecurityTests {
    @Autowired MockMvc mvc;
    @MockitoBean LeaveTypeService leaveTypes;
    @MockitoBean StaffLeaveRequestService leaveRequests;
    @MockitoBean JwtService jwt;
    private static final String TYPE = "{\"code\":\"ANNUAL\",\"name\":\"Annual\",\"active\":true}";
    private static final String REQUEST = "{\"leaveTypeId\":\"00000000-0000-0000-0000-000000000001\",\"startDate\":\"2026-09-14\",\"endDate\":\"2026-09-16\",\"reason\":\"Family leave\"}";
    private static final String REASON = "{\"reason\":\"Operational coverage required\"}";

    @ParameterizedTest
    @ValueSource(strings = {"CASHIER", "RECEPTIONIST", "PIT_SUPERVISOR", "DEALER", "DIRECTOR", "SUPER_ADMIN"})
    void authenticatedUsersCanUseOnlySelfServiceEndpoints(String role) throws Exception {
        mvc.perform(get("/api/hr/leave-types/available").with(user(role).roles(role)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/hr/leave").with(user(role).roles(role))
                        .contentType("application/json").content(REQUEST))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/hr/leave/me").with(user(role).roles(role)))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DIRECTOR", "SUPER_ADMIN"})
    void managementRolesCanManageTypesAndReadAllRequests(String role) throws Exception {
        mvc.perform(get("/api/hr/leave-types").with(user(role).roles(role))).andExpect(status().isOk());
        mvc.perform(post("/api/hr/leave-types").with(user(role).roles(role))
                        .contentType("application/json").content(TYPE)).andExpect(status().isCreated());
        mvc.perform(patch("/api/hr/leave-types/00000000-0000-0000-0000-000000000001")
                        .with(user(role).roles(role)).contentType("application/json")
                        .content("{\"name\":\"Annual Updated\",\"active\":true}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/hr/leave").with(user(role).roles(role))).andExpect(status().isOk());
        mvc.perform(get("/api/hr/leave/00000000-0000-0000-0000-000000000001")
                        .with(user(role).roles(role))).andExpect(status().isOk());
        mvc.perform(post("/api/hr/leave/00000000-0000-0000-0000-000000000001/approve")
                        .with(user(role).roles(role)).contentType("application/json").content("{}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/hr/leave/00000000-0000-0000-0000-000000000001/reject")
                        .with(user(role).roles(role)).contentType("application/json").content(REASON))
                .andExpect(status().isOk());
        mvc.perform(post("/api/hr/leave/00000000-0000-0000-0000-000000000001/cancel")
                        .with(user(role).roles(role)).contentType("application/json").content(REASON))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CASHIER", "RECEPTIONIST", "PIT_SUPERVISOR", "DEALER"})
    void operationalRolesCannotUseManagementEndpoints(String role) throws Exception {
        mvc.perform(get("/api/hr/leave-types").with(user(role).roles(role))).andExpect(status().isForbidden());
        mvc.perform(post("/api/hr/leave-types").with(user(role).roles(role))
                        .contentType("application/json").content(TYPE)).andExpect(status().isForbidden());
        mvc.perform(get("/api/hr/leave").with(user(role).roles(role))).andExpect(status().isForbidden());
        mvc.perform(get("/api/hr/leave/00000000-0000-0000-0000-000000000001")
                        .with(user(role).roles(role))).andExpect(status().isForbidden());
        mvc.perform(post("/api/hr/leave/00000000-0000-0000-0000-000000000001/approve")
                        .with(user(role).roles(role)).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/hr/leave-types/00000000-0000-0000-0000-000000000001")
                        .with(user(role).roles(role)).contentType("application/json")
                        .content("{\"name\":\"No\",\"active\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/hr/leave/00000000-0000-0000-0000-000000000001/reject")
                        .with(user(role).roles(role)).contentType("application/json").content(REASON))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/hr/leave/00000000-0000-0000-0000-000000000001/cancel")
                        .with(user(role).roles(role)).contentType("application/json").content(REASON))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/hr/leave/me/00000000-0000-0000-0000-000000000001/cancel")
                        .with(user(role).roles(role)).contentType("application/json").content(REASON))
                .andExpect(status().isOk());
    }

    @Test void unauthenticatedAccessIsDenied() throws Exception {
        mvc.perform(get("/api/hr/leave-types/available")).andExpect(status().isForbidden());
        mvc.perform(post("/api/hr/leave").contentType("application/json").content(REQUEST))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/hr/leave/me")).andExpect(status().isForbidden());
        mvc.perform(post("/api/hr/leave/me/00000000-0000-0000-0000-000000000001/cancel")
                        .contentType("application/json").content(REASON))
                .andExpect(status().isForbidden());
    }

    @Test void lifecycleReasonsAndRemarksAreValidatedAtTheApiBoundary() throws Exception {
        var director = user("director").roles("DIRECTOR");
        mvc.perform(post("/api/hr/leave/00000000-0000-0000-0000-000000000001/reject")
                        .with(director).contentType("application/json").content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/hr/leave/00000000-0000-0000-0000-000000000001/cancel")
                        .with(director).contentType("application/json").content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/hr/leave/00000000-0000-0000-0000-000000000001/approve")
                        .with(director).contentType("application/json")
                        .content("{\"remarks\":\"" + "x".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest());
    }
}
