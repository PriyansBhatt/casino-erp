package com.casino.casinoerp;

import com.casino.casinoerp.config.JwtAuthenticationFilter;
import com.casino.casinoerp.config.SecurityConfig;
import com.casino.casinoerp.controller.SessionSummaryController;
import com.casino.casinoerp.controller.VerifiedGamingResultController;
import com.casino.casinoerp.controller.WalletTransactionController;
import com.casino.casinoerp.dto.SessionFinancialPositionResponse;
import com.casino.casinoerp.service.JwtService;
import com.casino.casinoerp.service.SessionFinancialPositionService;
import com.casino.casinoerp.service.VerifiedGamingResultService;
import com.casino.casinoerp.service.WalletTransactionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({VerifiedGamingResultController.class, SessionSummaryController.class, WalletTransactionController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class SessionFinancialFoundationSecurityTests {
    private static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String RESULT_REQUEST = """
            {
              "customerId":"00000000-0000-0000-0000-000000000001",
              "customerSessionId":"00000000-0000-0000-0000-000000000002",
              "pitTableId":"00000000-0000-0000-0000-000000000003",
              "assignmentId":"00000000-0000-0000-0000-000000000004",
              "sourceType":"TABLE",
              "resultType":"WIN",
              "denominations":{"1000":1},
              "idempotencyKey":"security-test-key"
            }
            """;

    @Autowired MockMvc mockMvc;
    @MockitoBean VerifiedGamingResultService gamingResultService;
    @MockitoBean SessionFinancialPositionService positionService;
    @MockitoBean WalletTransactionService walletTransactionService;
    @MockitoBean JwtService jwtService;

    @ParameterizedTest
    @ValueSource(strings = {"PIT_SUPERVISOR", "DEALER", "SUPER_ADMIN"})
    void operationalGamingRolesReachCreateEndpoint(String role) throws Exception {
        mockMvc.perform(post("/api/verified-gaming-results")
                        .with(user(role.toLowerCase()).roles(role))
                        .contentType("application/json").content(RESULT_REQUEST))
                .andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CASHIER", "RECEPTIONIST", "DIRECTOR"})
    void nonGamingRolesCannotCreateVerifiedResult(String role) throws Exception {
        mockMvc.perform(post("/api/verified-gaming-results")
                        .with(user(role.toLowerCase()).roles(role))
                        .contentType("application/json").content(RESULT_REQUEST))
                .andExpect(status().isForbidden());
    }

    @Test void missingDenominationsRejected() throws Exception {
        mockMvc.perform(post("/api/verified-gaming-results")
                        .with(user("dealer").roles("DEALER"))
                        .contentType("application/json")
                        .content(RESULT_REQUEST.replace("{\"1000\":1}", "null")))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CASHIER", "DIRECTOR", "SUPER_ADMIN"})
    void financialRolesCanReadSafePosition(String role) throws Exception {
        when(positionService.getPosition(SESSION_ID)).thenReturn(position());
        mockMvc.perform(get("/api/session-summary/{id}", SESSION_ID)
                        .with(user(role.toLowerCase()).roles(role)))
                .andExpect(status().isOk());
    }

    @Test void receptionistCannotReadFinancialPosition() throws Exception {
        mockMvc.perform(get("/api/session-summary/{id}", SESSION_ID)
                        .with(user("reception").roles("RECEPTIONIST")))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"RECEPTIONIST", "CASHIER", "DIRECTOR", "SUPER_ADMIN"})
    void directWalletPostIsUnavailableToAuthenticatedUsers(String role) throws Exception {
        mockMvc.perform(post("/api/wallet-transactions")
                        .with(user(role.toLowerCase()).roles(role))
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }

    private SessionFinancialPositionResponse position() {
        return new SessionFinancialPositionResponse(
                UUID.fromString("00000000-0000-0000-0000-000000000001"), SESSION_ID,
                LocalDate.of(2026, 8, 8), new BigDecimal("1000"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1000"));
    }
}
