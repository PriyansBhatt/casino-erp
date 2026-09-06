package com.casino.casinoerp;

import com.casino.casinoerp.config.SecurityConfig;
import com.casino.casinoerp.controller.ChipCustodyController;
import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.entity.ChipCustodyLocationType;
import com.casino.casinoerp.service.ChipCustodyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChipCustodyController.class)
@Import({SecurityConfig.class, com.casino.casinoerp.config.JwtAuthenticationFilter.class})
class ChipCustodySecurityTests {
    @Autowired MockMvc mockMvc;
    @MockitoBean ChipCustodyService service;
    @MockitoBean com.casino.casinoerp.service.JwtService jwtService;

    private static final String BODY = "{\"denominations\":{\"1000\":10},\"idempotencyKey\":\"test-key\"}";
    private static final String CORRECTION_BODY = "{\"customerSessionId\":\"00000000-0000-0000-0000-000000000001\","
            + "\"denominations\":{\"5000\":1},\"reason\":\"Legacy pre-ledger custody correction\","
            + "\"idempotencyKey\":\"legacy-correction-test\"}";

    @Test void unauthenticatedRequestsAreDenied() throws Exception {
        mockMvc.perform(get("/api/chip-custody/cage")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/chip-custody/cage/opening").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test void cashierCanReadButCannotInitializeOrMoveTableFloat() throws Exception {
        when(service.cageInventory()).thenReturn(inventory());
        mockMvc.perform(get("/api/chip-custody/cage").with(user("cashier").roles("CASHIER")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/chip-custody/cage/opening").with(user("cashier").roles("CASHIER"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/chip-custody/tables/" + UUID.randomUUID() + "/float-issue")
                        .with(user("cashier").roles("CASHIER")).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test void pitSupervisorCanMoveTableFloatButCannotInitializeCage() throws Exception {
        UUID tableId = UUID.randomUUID();
        when(service.issueTableFloat(eq(tableId), any())).thenReturn(null);
        mockMvc.perform(post("/api/chip-custody/tables/" + tableId + "/float-issue")
                        .with(user("pit").roles("PIT_SUPERVISOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isCreated());
        mockMvc.perform(post("/api/chip-custody/cage/opening").with(user("pit").roles("PIT_SUPERVISOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
    }

    @Test void pitRolesCanTransferCustomerTableCustodyButCashierCannot() throws Exception {
        UUID tableId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        String path = "/api/chip-custody/tables/" + tableId + "/customer-sessions/" + sessionId + "/issue";
        mockMvc.perform(post(path).with(user("dealer").roles("DEALER"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());
        mockMvc.perform(post(path).with(user("pit").roles("PIT_SUPERVISOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());
        mockMvc.perform(post(path).with(user("cashier").roles("CASHIER"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test void onlyDirectorAndSuperAdminCanReadMovementHistory() throws Exception {
        when(service.currentHistory()).thenReturn(java.util.List.of());
        mockMvc.perform(get("/api/chip-custody/movements/current").with(user("cashier").roles("CASHIER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/chip-custody/movements/current").with(user("director").roles("DIRECTOR")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/chip-custody/movements/current").with(user("admin").roles("SUPER_ADMIN")))
                .andExpect(status().isOk());
    }

    @Test void onlySuperAdminCanCorrectLegacySessionCustody() throws Exception {
        String path = "/api/chip-custody/legacy-session-correction";
        mockMvc.perform(post(path).with(user("admin").roles("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(CORRECTION_BODY))
                .andExpect(status().isCreated());
        mockMvc.perform(post(path).with(user("director").roles("DIRECTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(CORRECTION_BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(path).with(user("cashier").roles("CASHIER"))
                        .contentType(MediaType.APPLICATION_JSON).content(CORRECTION_BODY))
                .andExpect(status().isForbidden());
    }

    @Test void legacyCorrectionRequiresAReasonOfSensibleLength() throws Exception {
        mockMvc.perform(post("/api/chip-custody/legacy-session-correction")
                        .with(user("admin").roles("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORRECTION_BODY.replace("Legacy pre-ledger custody correction", "short")))
                .andExpect(status().isBadRequest());
    }

    private ChipCustodyInventoryResponse inventory() {
        return new ChipCustodyInventoryResponse(ChipCustodyLocationType.CAGE, null, true,
                Map.of(1000, 10L), new BigDecimal("10000"));
    }
}
