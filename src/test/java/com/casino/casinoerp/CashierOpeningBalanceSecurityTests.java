package com.casino.casinoerp;

import com.casino.casinoerp.config.JwtAuthenticationFilter;
import com.casino.casinoerp.config.SecurityConfig;
import com.casino.casinoerp.controller.CashierOpeningBalanceController;
import com.casino.casinoerp.service.CashierOpeningBalanceService;
import com.casino.casinoerp.service.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CashierOpeningBalanceController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class CashierOpeningBalanceSecurityTests {
    @Autowired MockMvc mockMvc;
    @MockitoBean CashierOpeningBalanceService service;
    @MockitoBean JwtService jwtService;
    private static final String BODY = "{\"openingCashAmount\":1000}";

    @ParameterizedTest
    @ValueSource(strings = {"CASHIER", "DIRECTOR", "SUPER_ADMIN"})
    void reconciliationReviewRolesCanReadOwnCurrentBalance(String role) throws Exception {
        mockMvc.perform(get("/api/cashier-opening-balances/current").with(user(role).roles(role)))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CASHIER", "SUPER_ADMIN"})
    void operationalRolesCanCreateOwnBalance(String role) throws Exception {
        mockMvc.perform(post("/api/cashier-opening-balances/current").with(user(role).roles(role))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());
    }

    @Test
    void directorCannotCreateOpeningBalance() throws Exception {
        mockMvc.perform(post("/api/cashier-opening-balances/current").with(user("director").roles("DIRECTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedRequestsAreDenied() throws Exception {
        mockMvc.perform(get("/api/cashier-opening-balances/current")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/cashier-opening-balances/current")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }
}
