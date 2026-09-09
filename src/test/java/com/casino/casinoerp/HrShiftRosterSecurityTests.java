package com.casino.casinoerp;

import com.casino.casinoerp.config.*;import com.casino.casinoerp.controller.*;import com.casino.casinoerp.service.*;
import org.junit.jupiter.api.*;import org.junit.jupiter.params.ParameterizedTest;import org.junit.jupiter.params.provider.ValueSource;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;import org.springframework.context.annotation.Import;import org.springframework.test.context.bean.override.mockito.MockitoBean;import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({ShiftDefinitionController.class,StaffRosterController.class}) @Import({SecurityConfig.class,JwtAuthenticationFilter.class})
class HrShiftRosterSecurityTests {
    @Autowired MockMvc mvc;@MockitoBean ShiftDefinitionService shifts;@MockitoBean StaffRosterService roster;@MockitoBean JwtService jwt;
    private static final String SHIFT="{\"code\":\"NIGHT\",\"name\":\"Night\",\"startTime\":\"18:00:00\",\"endTime\":\"03:30:00\",\"crossesMidnight\":true,\"lateGraceMinutes\":5,\"earlyCheckInMinutes\":30}";
    @ParameterizedTest @ValueSource(strings={"DIRECTOR","SUPER_ADMIN"}) void managersCanReadAndWrite(String role)throws Exception{mvc.perform(get("/api/hr/shifts").with(user(role).roles(role))).andExpect(status().isOk());mvc.perform(post("/api/hr/shifts").with(user(role).roles(role)).contentType("application/json").content(SHIFT)).andExpect(status().isCreated());mvc.perform(get("/api/hr/roster").with(user(role).roles(role))).andExpect(status().isOk());}
    @ParameterizedTest @ValueSource(strings={"CASHIER","RECEPTIONIST","PIT_SUPERVISOR","DEALER"}) void operationalRolesAreForbidden(String role)throws Exception{mvc.perform(get("/api/hr/shifts").with(user(role).roles(role))).andExpect(status().isForbidden());mvc.perform(get("/api/hr/roster").with(user(role).roles(role))).andExpect(status().isForbidden());mvc.perform(post("/api/hr/shifts").with(user(role).roles(role)).contentType("application/json").content(SHIFT)).andExpect(status().isForbidden());}
    @Test void unauthenticatedAccessDenied()throws Exception{mvc.perform(get("/api/hr/shifts")).andExpect(status().isForbidden());}
}
