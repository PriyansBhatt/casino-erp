package com.casino.casinoerp;

import com.casino.casinoerp.config.JwtAuthenticationFilter;
import com.casino.casinoerp.config.SecurityConfig;
import com.casino.casinoerp.controller.PitTableController;
import com.casino.casinoerp.entity.PitTable;
import com.casino.casinoerp.service.JwtService;
import com.casino.casinoerp.service.PitTableService;
import com.casino.casinoerp.service.PitTableReconciliationService;
import com.casino.casinoerp.service.LegacyPitTableReconciliationService;
import com.casino.casinoerp.service.PitTableOperationService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PitTableController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class PitTableControllerSecurityTests {
    private static final UUID TABLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final String OPEN_REQUEST = """
            {"denominations":{"5000":20},"remarks":"Opening float",
             "idempotencyKey":"open-table-test-1"}
            """;
    @Autowired MockMvc mockMvc;
    @MockitoBean PitTableService service;
    @MockitoBean PitTableReconciliationService reconciliationService;
    @MockitoBean LegacyPitTableReconciliationService legacyReconciliationService;
    @MockitoBean PitTableOperationService operationService;
    @MockitoBean JwtService jwtService;

    @ParameterizedTest
    @ValueSource(strings = {"PIT_SUPERVISOR", "DEALER", "SUPER_ADMIN"})
    void pitRolesCanListPhysicalTables(String role) throws Exception {
        PitTable table = table();
        when(operationService.overview()).thenReturn(List.of(new com.casino.casinoerp.dto.PitTableOverviewResponse(
                TABLE_ID, table.getTableCode(), table.getTableName(), table.getGameType(), null,
                "ACTIVE", TABLE_ID, java.time.LocalDate.of(2026, 9, 2), "OPEN",
                table.getOpeningFloat(), 0, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO)));
        var actor = user(role.toLowerCase()).roles(role);
        mockMvc.perform(get("/api/pit-tables").with(actor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].physicalTableId").value(TABLE_ID.toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"DIRECTOR", "CASHIER", "RECEPTIONIST"})
    void unrelatedRolesCannotListPhysicalTables(String role) throws Exception {
        var actor = user(role.toLowerCase()).roles(role);
        mockMvc.perform(get("/api/pit-tables").with(actor)).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PIT_SUPERVISOR", "SUPER_ADMIN"})
    void authorizedRolesCanOpenPhysicalTableOperation(String role) throws Exception {
        when(operationService.open(org.mockito.ArgumentMatchers.eq(TABLE_ID),
                org.mockito.ArgumentMatchers.any())).thenReturn(new com.casino.casinoerp.dto.PitTableResponse(
                        TABLE_ID, "T-BAC-10", "Baccarat Table 10", "Baccarat", "OPEN",
                        java.time.LocalDate.of(2026, 9, 2), java.time.LocalDateTime.now(), null,
                        new java.math.BigDecimal("100000"), null, "Opening float"));
        mockMvc.perform(post("/api/pit-tables/physical/{physicalTableId}/open", TABLE_ID)
                        .with(user(role.toLowerCase()).roles(role))
                        .contentType("application/json").content(OPEN_REQUEST))
                .andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DEALER", "DIRECTOR", "CASHIER", "RECEPTIONIST"})
    void unauthorizedRolesCannotOpenPhysicalTableOperation(String role) throws Exception {
        mockMvc.perform(post("/api/pit-tables/physical/{physicalTableId}/open", TABLE_ID)
                        .with(user(role.toLowerCase()).roles(role))
                        .contentType("application/json").content(OPEN_REQUEST))
                .andExpect(status().isForbidden());
    }

    @org.junit.jupiter.api.Test void prototypeIdCannotResolveAsAuthoritativeUuid() throws Exception {
        mockMvc.perform(get("/api/pit-tables/T-BAC-1").with(user("dealer").roles("DEALER")))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PIT_SUPERVISOR", "DEALER", "SUPER_ADMIN"})
    void pitRolesCanCloseTables(String role) throws Exception {
        when(service.closeTable(TABLE_ID, new java.math.BigDecimal("90000"))).thenReturn(table());
        mockMvc.perform(put("/api/pit-tables/{tableId}/close", TABLE_ID)
                        .param("closingFloat", "90000").with(user(role.toLowerCase()).roles(role)))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DIRECTOR", "CASHIER", "RECEPTIONIST"})
    void unrelatedRolesCannotCloseTables(String role) throws Exception {
        mockMvc.perform(put("/api/pit-tables/{tableId}/close", TABLE_ID)
                        .param("closingFloat", "90000").with(user(role.toLowerCase()).roles(role)))
                .andExpect(status().isForbidden());
    }

    @org.junit.jupiter.api.Test
    void onlySuperAdminCanResolveLegacyTableReconciliation() throws Exception {
        String body = """
                {"physicalClosingFloat":0,
                 "reason":"Legacy table predates authoritative physical custody tracking.",
                 "idempotencyKey":"legacy-table-1"}
                """;
        mockMvc.perform(post("/api/pit-tables/{tableId}/legacy-reconciliation-resolution", TABLE_ID)
                        .with(user("superadmin").roles("SUPER_ADMIN"))
                        .contentType("application/json").content(body))
                .andExpect(status().isOk());

        for (String role : List.of("DIRECTOR", "CASHIER", "PIT_SUPERVISOR", "DEALER", "RECEPTIONIST")) {
            mockMvc.perform(post("/api/pit-tables/{tableId}/legacy-reconciliation-resolution", TABLE_ID)
                            .with(user(role.toLowerCase()).roles(role))
                            .contentType("application/json").content(body))
                    .andExpect(status().isForbidden());
        }
    }

    private PitTable table() {
        PitTable table = new PitTable(); table.setId(TABLE_ID); table.setTableCode("T-BAC-10");
        table.setTableName("Baccarat Table 10"); table.setGameType("Baccarat"); table.setStatus("OPEN");
        table.setOpeningFloat(new java.math.BigDecimal("100000"));
        return table;
    }
}
