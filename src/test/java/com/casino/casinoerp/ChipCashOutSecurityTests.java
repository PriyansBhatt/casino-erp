package com.casino.casinoerp;

import com.casino.casinoerp.config.JwtAuthenticationFilter;
import com.casino.casinoerp.config.SecurityConfig;
import com.casino.casinoerp.controller.ChipCashOutController;
import com.casino.casinoerp.dto.ChipCashOutResponse;
import com.casino.casinoerp.service.ChipCashOutService;
import com.casino.casinoerp.service.JwtService;
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
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChipCashOutController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class ChipCashOutSecurityTests {
    private static final String REQUEST = """
            {
              "customerId":"00000000-0000-0000-0000-000000000001",
              "customerSessionId":"00000000-0000-0000-0000-000000000002",
              "cashPaid":1000,
              "totalChipValueReturned":1000,
              "denominations":{"1000":1},
              "paymentMode":"CASH",
              "idempotencyKey":"cashout-test-key"
            }
            """;

    @Autowired MockMvc mockMvc;
    @MockitoBean ChipCashOutService service;
    @MockitoBean JwtService jwtService;

    @ParameterizedTest
    @ValueSource(strings = {"CASHIER", "SUPER_ADMIN"})
    void permittedRolesCanCreateCashOut(String role) throws Exception {
        when(service.create(any())).thenReturn(response());
        mockMvc.perform(post("/api/cashouts").with(user(role.toLowerCase()).roles(role))
                        .contentType("application/json").content(REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.cashOutCode").value("CO-20260808-test"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"RECEPTIONIST", "DIRECTOR", "DEALER", "PIT_SUPERVISOR"})
    void otherRolesCannotCreateCashOut(String role) throws Exception {
        mockMvc.perform(post("/api/cashouts").with(user(role.toLowerCase()).roles(role))
                        .contentType("application/json").content(REQUEST))
                .andExpect(status().isForbidden());
    }

    @Test void unauthenticatedRequestIsDenied() throws Exception {
        mockMvc.perform(post("/api/cashouts").contentType("application/json").content(REQUEST))
                .andExpect(status().isForbidden());
    }

    @Test void nonPositiveAmountsAreRejected() throws Exception {
        mockMvc.perform(post("/api/cashouts").with(user("cashier").roles("CASHIER"))
                        .contentType("application/json").content(REQUEST.replace("1000", "0")))
                .andExpect(status().isBadRequest());
    }

    @Test void invalidPaymentModeIsRejected() throws Exception {
        mockMvc.perform(post("/api/cashouts").with(user("cashier").roles("CASHIER"))
                        .contentType("application/json").content(REQUEST.replace("\"CASH\"", "\"CRYPTO\"")))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.5", "\"1\"", "-1", "null", "9223372036854775808", "true"})
    void invalidQuantitiesReturnControlled400(String quantity) throws Exception {
        mockMvc.perform(post("/api/cashouts").with(user("cashier").roles("CASHIER"))
                .contentType("application/json").content(REQUEST.replace("\"1000\":1", "\"1000\":" + quantity)))
                .andExpect(status().isBadRequest());
        org.mockito.Mockito.verify(service, org.mockito.Mockito.never()).create(any());
    }

    private ChipCashOutResponse response() {
        return new ChipCashOutResponse(UUID.randomUUID(), "CO-20260808-test",
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                new BigDecimal("1000"), new BigDecimal("1000"), java.util.Map.of(1000, 1L), "CASH", null,
                LocalDate.of(2026, 8, 8), LocalDateTime.now(), null, null);
    }
}
