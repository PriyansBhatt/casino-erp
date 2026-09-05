package com.casino.casinoerp;

import com.casino.casinoerp.config.JwtAuthenticationFilter;
import com.casino.casinoerp.config.SecurityConfig;
import com.casino.casinoerp.controller.PitTablePlayerController;
import com.casino.casinoerp.service.JwtService;
import com.casino.casinoerp.service.PitTableCustomerAssignmentService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PitTablePlayerController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class PitTablePlayerSecurityTests {
    private static final UUID TABLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID ASSIGNMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final String REQUEST = """
            {"customerId":"00000000-0000-0000-0000-000000000001",
             "customerSessionId":"00000000-0000-0000-0000-000000000002"}
            """;
    private static final String LEAVE_REQUEST = """
            {"denominations":{},"idempotencyKey":"leave-settlement-key"}
            """;

    @Autowired MockMvc mockMvc;
    @MockitoBean PitTableCustomerAssignmentService service;
    @MockitoBean JwtService jwtService;

    @ParameterizedTest
    @ValueSource(strings = {"PIT_SUPERVISOR", "DEALER", "SUPER_ADMIN"})
    void pitRolesCanReachAssignmentEndpoints(String role) throws Exception {
        var actor = user(role.toLowerCase()).roles(role);
        mockMvc.perform(get("/api/pit/tables/{tableId}/players", TABLE_ID).with(actor))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/pit/tables/{tableId}/players", TABLE_ID).with(actor)
                        .contentType("application/json").content(REQUEST))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/pit/tables/{tableId}/players/{assignmentId}/leave", TABLE_ID, ASSIGNMENT_ID)
                        .with(actor).contentType("application/json").content(LEAVE_REQUEST))
                .andExpect(status().isOk());
    }

    @org.junit.jupiter.api.Test
    void leaveCannotBypassExplicitCustodySettlementRequest() throws Exception {
        mockMvc.perform(post("/api/pit/tables/{tableId}/players/{assignmentId}/leave", TABLE_ID, ASSIGNMENT_ID)
                        .with(user("pit").roles("PIT_SUPERVISOR")))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"RECEPTIONIST", "CASHIER", "DIRECTOR"})
    void nonPitRolesCannotUseAssignmentEndpoints(String role) throws Exception {
        mockMvc.perform(post("/api/pit/tables/{tableId}/players", TABLE_ID)
                        .with(user(role.toLowerCase()).roles(role))
                        .contentType("application/json").content(REQUEST))
                .andExpect(status().isForbidden());
    }
}
