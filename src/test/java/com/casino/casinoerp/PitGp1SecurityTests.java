package com.casino.casinoerp;

import com.casino.casinoerp.config.*;
import com.casino.casinoerp.controller.*;
import com.casino.casinoerp.service.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.*;

@WebMvcTest({PitTableController.class,PitTablePlayerController.class,VerifiedGamingResultController.class})
@Import({SecurityConfig.class,JwtAuthenticationFilter.class})
class PitGp1SecurityTests {
    @Autowired MockMvc mvc;
    @MockitoBean PitTableService tables;
    @MockitoBean PitTableReconciliationService reconciliation;
    @MockitoBean LegacyPitTableReconciliationService legacy;
    @MockitoBean PitTableOperationService operations;
    @MockitoBean PitTableModeService mode;
    @MockitoBean PitTableCustomerAssignmentService players;
    @MockitoBean VerifiedGamingResultService results;
    @MockitoBean JwtService jwt;
    private static final String ID="00000000-0000-0000-0000-000000000001";
    @ParameterizedTest @ValueSource(strings={"1.5","\"2\"","\"text\"","null","-1","9223372036854775808","true"})
    void openingAndLeaveRejectInvalidJsonQuantities(String quantity) throws Exception {
        String body="{\"denominations\":{\"500\":"+quantity+"},\"idempotencyKey\":\"gp1\",\"expectedBusinessDate\":\"2026-09-02\"}";
        mvc.perform(post("/api/pit-tables/physical/"+ID+"/open").with(user("pit").roles("PIT_SUPERVISOR")).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/pit/tables/"+ID+"/players/"+ID+"/leave").with(user("pit").roles("PIT_SUPERVISOR")).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(operations,players);
    }
    @ParameterizedTest @ValueSource(strings={"1.5","\"2\"","\"text\"","null","-1","2147483648","true"})
    void winAndLossRejectInvalidJsonQuantities(String quantity) throws Exception {
        for(String type:new String[]{"WIN","LOSS"}) {
            String body="{\"customerId\":\""+ID+"\",\"customerSessionId\":\""+ID+"\",\"pitTableId\":\""+ID+"\",\"assignmentId\":\""+ID+"\",\"sourceType\":\"TABLE\",\"resultType\":\""+type+"\",\"denominations\":{\"500\":"+quantity+"},\"idempotencyKey\":\"gp1\"}";
            mvc.perform(post("/api/verified-gaming-results").with(user("pit").roles("PIT_SUPERVISOR")).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(results);
    }
    @org.junit.jupiter.api.Test void expectedDateRequiredForOpening() throws Exception {
        mvc.perform(post("/api/pit-tables/physical/"+ID+"/open").with(user("pit").roles("PIT_SUPERVISOR")).contentType("application/json").content("{\"denominations\":{\"500\":1},\"idempotencyKey\":\"gp1\"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(operations);
    }
}
