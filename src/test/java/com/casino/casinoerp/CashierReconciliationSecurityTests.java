package com.casino.casinoerp;

import com.casino.casinoerp.config.JwtAuthenticationFilter;
import com.casino.casinoerp.config.SecurityConfig;
import com.casino.casinoerp.controller.CashierReconciliationController;
import com.casino.casinoerp.service.CashierReconciliationService;
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

@WebMvcTest(CashierReconciliationController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class CashierReconciliationSecurityTests {
    @Autowired MockMvc mockMvc;
    @MockitoBean CashierReconciliationService service;
    @MockitoBean JwtService jwtService;
    private static final String BODY = "{\"expectedBusinessDate\":\"2026-08-08\",\"openingCash\":0,\"denominations\":{},\"idempotencyKey\":\"test-key\"}";

    @ParameterizedTest @ValueSource(strings = {"CASHIER", "DIRECTOR", "SUPER_ADMIN"})
    void approvedRolesCanView(String role) throws Exception {
        mockMvc.perform(get("/api/cashier-reconciliation/current").with(user(role).roles(role))).andExpect(status().isOk());
    }

    @ParameterizedTest @ValueSource(strings = {"CASHIER", "SUPER_ADMIN"})
    void approvedRolesCanPreviewAndSubmit(String role) throws Exception {
        mockMvc.perform(post("/api/cashier-reconciliation/preview").with(user(role).roles(role)).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isOk());
        mockMvc.perform(post("/api/cashier-reconciliation/submit").with(user(role).roles(role)).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isOk());
    }

    @ParameterizedTest @ValueSource(strings = {"RECEPTIONIST", "PIT_SUPERVISOR", "DEALER", "ACCOUNTS"})
    void unrelatedRolesAreDenied(String role) throws Exception {
        mockMvc.perform(get("/api/cashier-reconciliation/current").with(user(role).roles(role))).andExpect(status().isForbidden());
    }

    @Test void directorCannotSubmit() throws Exception {
        mockMvc.perform(post("/api/cashier-reconciliation/submit").with(user("director").roles("DIRECTOR")).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
    }

    @ParameterizedTest @ValueSource(strings = {"DIRECTOR", "SUPER_ADMIN"})
    void privilegedRolesCanListAndReopen(String role) throws Exception {
        mockMvc.perform(get("/api/cashier-reconciliation/current/submitted").with(user(role).roles(role)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/cashier-reconciliation/00000000-0000-0000-0000-000000000001/reopen")
                .with(user(role).roles(role)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Correction required\"}"))
                .andExpect(status().isOk());
    }

    @Test void cashierCannotReopen() throws Exception {
        mockMvc.perform(post("/api/cashier-reconciliation/00000000-0000-0000-0000-000000000001/reopen")
                .with(user("cashier").roles("CASHIER")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Correction\"}"))
                .andExpect(status().isForbidden());
    }

    @Test void blankReopenReasonIsRejected() throws Exception {
        mockMvc.perform(post("/api/cashier-reconciliation/00000000-0000-0000-0000-000000000001/reopen")
                .with(user("director").roles("DIRECTOR")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test void unauthenticatedRequestsAreDenied() throws Exception {
        mockMvc.perform(get("/api/cashier-reconciliation/current")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/cashier-reconciliation/submit").contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
    }
    @ParameterizedTest @ValueSource(strings={"1.5", "\"2\"", "null", "-1", "2147483648", "true"})
    void malformedNoteQuantitiesReturn400(String quantity) throws Exception {
        mockMvc.perform(post("/api/cashier-reconciliation/submit").with(user("cashier").roles("CASHIER"))
                .contentType(MediaType.APPLICATION_JSON).content(BODY.replace("\"denominations\":{}", "\"denominations\":{\"1000\":"+quantity+"}")))
                .andExpect(status().isBadRequest());
        org.mockito.Mockito.verify(service, org.mockito.Mockito.never()).submit(org.mockito.ArgumentMatchers.any());
    }
    @Test void expectedDateIsRequired() throws Exception {
        mockMvc.perform(post("/api/cashier-reconciliation/submit").with(user("cashier").roles("CASHIER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"denominations\":{},\"idempotencyKey\":\"k\"}"))
                .andExpect(status().isBadRequest());
    }
}
