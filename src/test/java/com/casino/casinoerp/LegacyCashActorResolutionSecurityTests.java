package com.casino.casinoerp;

import com.casino.casinoerp.config.JwtAuthenticationFilter;
import com.casino.casinoerp.config.SecurityConfig;
import com.casino.casinoerp.controller.LegacyCashActorResolutionController;
import com.casino.casinoerp.service.JwtService;
import com.casino.casinoerp.service.LegacyCashActorResolutionService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LegacyCashActorResolutionController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class LegacyCashActorResolutionSecurityTests {
    @Autowired MockMvc mockMvc;
    @MockitoBean LegacyCashActorResolutionService service;
    @MockitoBean JwtService jwtService;
    private static final String PATH = "/api/cashier-reconciliation/legacy-actor-resolution/"
            + "00000000-0000-0000-0000-000000000001";
    private static final String BODY = "{\"reason\":\"Legacy actor accounting review completed.\","
            + "\"idempotencyKey\":\"legacy-admin-001\"}";

    @Test
    void superAdminCanResolve() throws Exception {
        mockMvc.perform(post(PATH).with(user("superadmin").roles("SUPER_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DIRECTOR", "CASHIER", "RECEPTIONIST"})
    void otherRolesAreForbidden(String role) throws Exception {
        mockMvc.perform(post(PATH).with(user(role).roles(role))
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedRequestIsForbidden() throws Exception {
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }
}
