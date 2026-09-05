package com.casino.casinoerp;

import com.casino.casinoerp.config.JwtAuthenticationFilter;
import com.casino.casinoerp.config.SecurityConfig;
import com.casino.casinoerp.controller.ChipBuyInController;
import com.casino.casinoerp.dto.ChipBuyInResponse;
import com.casino.casinoerp.dto.ChipBuyInHistoryResponse;
import com.casino.casinoerp.service.ChipBuyInService;
import com.casino.casinoerp.service.JwtService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChipBuyInController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class ChipBuyInSecurityTests {

    private static final String REQUEST = """
            {
              "customerId":"00000000-0000-0000-0000-000000000001",
              "customerSessionId":"00000000-0000-0000-0000-000000000002",
              "amountReceived":10000,
              "paymentMode":"CASH",
              "totalChipValueIssued":10000,
              "denominations":{"1000":10},
              "idempotencyKey":"test-key-1"
            }
            """;

    @Autowired MockMvc mockMvc;
    @MockitoBean ChipBuyInService service;
    @MockitoBean JwtService jwtService;

    @ParameterizedTest
    @ValueSource(strings = {"CASHIER", "SUPER_ADMIN"})
    void authorizedRolesCanCreateBuyIn(String role) throws Exception {
        when(service.create(any())).thenReturn(response());

        mockMvc.perform(post("/api/buyins")
                        .with(user(role.toLowerCase()).roles(role))
                        .contentType("application/json")
                        .content(REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.buyInCode").value("BI-20260808-test"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"RECEPTIONIST", "DIRECTOR"})
    void otherAuthenticatedRolesCannotCreateBuyIn(String role) throws Exception {
        mockMvc.perform(post("/api/buyins")
                        .with(user(role.toLowerCase()).roles(role))
                        .contentType("application/json")
                        .content(REQUEST))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedRequestIsDenied() throws Exception {
        mockMvc.perform(post("/api/buyins").contentType("application/json").content(REQUEST))
                .andExpect(status().isForbidden());
    }

    @Test
    void nonPositiveAmountsAreRejectedByRequestValidation() throws Exception {
        mockMvc.perform(post("/api/buyins")
                        .with(user("cashier").roles("CASHIER"))
                        .contentType("application/json")
                        .content(REQUEST.replace("10000", "0")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidPaymentModeIsRejected() throws Exception {
        mockMvc.perform(post("/api/buyins")
                        .with(user("cashier").roles("CASHIER"))
                        .contentType("application/json")
                        .content(REQUEST.replace("\"CASH\"", "\"CRYPTO\"")))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CASHIER", "DIRECTOR", "SUPER_ADMIN"})
    void authorizedRolesCanReadCurrentBusinessDateHistory(String role) throws Exception {
        when(service.getCurrentBusinessDateHistory())
                .thenReturn(List.of(new ChipBuyInHistoryResponse(response(), "CUS-1001", "Test Customer")));

        mockMvc.perform(get("/api/buyins/current")
                        .with(user(role.toLowerCase()).roles(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].transaction.buyInCode").value("BI-20260808-test"))
                .andExpect(jsonPath("$.data[0].customerCode").value("CUS-1001"));
    }

    @Test
    void receptionistCannotReadCurrentBusinessDateHistory() throws Exception {
        mockMvc.perform(get("/api/buyins/current")
                        .with(user("reception").roles("RECEPTIONIST")))
                .andExpect(status().isForbidden());
    }

    private ChipBuyInResponse response() {
        return new ChipBuyInResponse(
                UUID.randomUUID(), "BI-20260808-test",
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                new BigDecimal("10000"), "CASH", new BigDecimal("10000"), java.util.Map.of(1000, 10L), null,
                LocalDate.of(2026, 8, 8), LocalDateTime.now(), null, null);
    }
}
