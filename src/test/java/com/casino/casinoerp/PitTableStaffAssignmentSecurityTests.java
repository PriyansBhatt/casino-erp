package com.casino.casinoerp;

import com.casino.casinoerp.config.JwtAuthenticationFilter;
import com.casino.casinoerp.config.SecurityConfig;
import com.casino.casinoerp.controller.PitTableStaffAssignmentController;
import com.casino.casinoerp.service.JwtService;
import com.casino.casinoerp.service.PitTableStaffAssignmentService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PitTableStaffAssignmentController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class PitTableStaffAssignmentSecurityTests {
    private static final UUID TABLE_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID ASSIGNMENT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID STAFF_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final String ASSIGN = """
            {"staffUserId":"30000000-0000-0000-0000-000000000001",
             "assignmentRole":"DEALER","remarks":"Shift assignment","idempotencyKey":"assign-1"}
            """;
    private static final String END = """
            {"remarks":"Shift ended","idempotencyKey":"end-1"}
            """;
    private static final String HANDOVER = """
            {"newStaffUserId":"30000000-0000-0000-0000-000000000001",
             "remarks":"Shift handover","idempotencyKey":"handover-1"}
            """;

    @Autowired MockMvc mvc;
    @MockitoBean PitTableStaffAssignmentService service;
    @MockitoBean JwtService jwtService;

    @ParameterizedTest
    @ValueSource(strings = {"SUPER_ADMIN", "PIT_SUPERVISOR", "DEALER"})
    void pitRolesCanReadStaffOnUuidShapedOperationPath(String role) throws Exception {
        when(service.getActive(TABLE_ID)).thenReturn(List.of());
        when(service.getHistory(TABLE_ID)).thenReturn(List.of());
        mvc.perform(get("/api/pit-tables/{tableId}/staff", TABLE_ID)
                        .with(user(role.toLowerCase()).roles(role)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/pit-tables/{tableId}/staff/history", TABLE_ID)
                        .with(user(role.toLowerCase()).roles(role)))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CASHIER", "RECEPTIONIST"})
    void unrelatedRolesCannotReadStaff(String role) throws Exception {
        mvc.perform(get("/api/pit-tables/{tableId}/staff", TABLE_ID)
                        .with(user(role.toLowerCase()).roles(role)))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUPER_ADMIN", "PIT_SUPERVISOR"})
    void managersCanUseCandidateAssignEndAndHandoverEndpoints(String role) throws Exception {
        when(service.getCandidates(com.casino.casinoerp.entity.PitTableStaffAssignmentRole.DEALER))
                .thenReturn(List.of());
        var actor = user(role.toLowerCase()).roles(role);
        mvc.perform(get("/api/pit-tables/staff/candidates").param("role", "DEALER").with(actor))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pit-tables/{tableId}/staff", TABLE_ID).with(actor)
                        .contentType("application/json").content(ASSIGN))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/pit-tables/{tableId}/staff/{assignmentId}/end", TABLE_ID, ASSIGNMENT_ID)
                        .with(actor).contentType("application/json").content(END))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pit-tables/{tableId}/staff/DEALER/handover", TABLE_ID)
                        .with(actor).contentType("application/json").content(HANDOVER))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DEALER", "CASHIER", "RECEPTIONIST"})
    void unauthorizedRolesCannotManageStaffOrListCandidates(String role) throws Exception {
        var actor = user(role.toLowerCase()).roles(role);
        mvc.perform(get("/api/pit-tables/staff/candidates").param("role", "DEALER").with(actor))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/pit-tables/{tableId}/staff", TABLE_ID).with(actor)
                        .contentType("application/json").content(ASSIGN))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/pit-tables/{tableId}/staff/{assignmentId}/end", TABLE_ID, ASSIGNMENT_ID)
                        .with(actor).contentType("application/json").content(END))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/pit-tables/{tableId}/staff/DEALER/handover", TABLE_ID)
                        .with(actor).contentType("application/json").content(HANDOVER))
                .andExpect(status().isForbidden());
    }
}
