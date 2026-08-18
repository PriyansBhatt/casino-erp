package com.casino.casinoerp;

import com.casino.casinoerp.config.*;
import com.casino.casinoerp.controller.CustomerBonusController;
import com.casino.casinoerp.dto.CustomerBonusResponse;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CustomerBonusController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class CustomerBonusSecurityTests {
    private static final String REQUEST = """
            {"customerId":"00000000-0000-0000-0000-000000000001",
             "customerSessionId":"00000000-0000-0000-0000-000000000002",
             "bonusType":"PROMOTIONAL","amount":2500,"reason":"Campaign","idempotencyKey":"bonus-1"}
            """;
    @Autowired MockMvc mockMvc;
    @MockitoBean CustomerBonusService service;
    @MockitoBean JwtService jwtService;

    @ParameterizedTest
    @ValueSource(strings = {"DIRECTOR", "SUPER_ADMIN"})
    void managementRolesCanCreateAndRead(String role) throws Exception {
        when(service.create(any())).thenReturn(response());
        when(service.getByBusinessDate(any())).thenReturn(List.of(response()));
        mockMvc.perform(post("/api/customer-bonuses").with(user(role).roles(role))
                        .contentType("application/json").content(REQUEST))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/customer-bonuses?businessDate=2026-08-08").with(user(role).roles(role)))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CASHIER", "RECEPTIONIST", "PIT_SUPERVISOR", "DEALER"})
    void operationalRolesAreDenied(String role) throws Exception {
        mockMvc.perform(post("/api/customer-bonuses").with(user(role).roles(role))
                        .contentType("application/json").content(REQUEST))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/customer-bonuses").with(user(role).roles(role)))
                .andExpect(status().isForbidden());
    }

    @Test
    void zeroAndNegativeAmountsAreRejected() throws Exception {
        for (String amount : List.of("0", "-1")) {
            mockMvc.perform(post("/api/customer-bonuses").with(user("director").roles("DIRECTOR"))
                            .contentType("application/json").content(REQUEST.replace("2500", amount)))
                    .andExpect(status().isBadRequest());
        }
    }

    private CustomerBonusResponse response() {
        return new CustomerBonusResponse(UUID.randomUUID(), "BON-20260808-test", UUID.randomUUID(),
                "CUS-1001", "Test Customer", UUID.randomUUID(), "SES-1", LocalDate.of(2026, 8, 8),
                CustomerBonusType.PROMOTIONAL, new BigDecimal("2500"), "Campaign", CustomerBonusStatus.APPROVED,
                null, null, LocalDateTime.now(), LocalDateTime.now());
    }
}
