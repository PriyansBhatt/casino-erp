package com.casino.casinoerp;

import com.casino.casinoerp.config.*;
import com.casino.casinoerp.controller.*;
import com.casino.casinoerp.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({HrMasterDataController.class,StaffProfileController.class})
@Import({SecurityConfig.class,JwtAuthenticationFilter.class})
class HrSecurityTests {
    @Autowired MockMvc mvc; @MockitoBean HrMasterDataService masterData; @MockitoBean StaffProfileService staff; @MockitoBean JwtService jwt;
    private static final String DEPARTMENT="{\"code\":\"HR\",\"name\":\"Human Resources\"}";

    @ParameterizedTest @ValueSource(strings={"DIRECTOR","SUPER_ADMIN"})
    void managementRolesCanReadAndWrite(String role)throws Exception{
        mvc.perform(get("/api/hr/staff").with(user(role).roles(role))).andExpect(status().isOk());
        mvc.perform(post("/api/hr/departments").with(user(role).roles(role)).contentType("application/json").content(DEPARTMENT)).andExpect(status().isCreated());
    }
    @ParameterizedTest @ValueSource(strings={"CASHIER","RECEPTIONIST","PIT_SUPERVISOR","DEALER"})
    void operationalRolesCannotManageHr(String role)throws Exception{
        mvc.perform(get("/api/hr/staff").with(user(role).roles(role))).andExpect(status().isForbidden());
        mvc.perform(post("/api/hr/departments").with(user(role).roles(role)).contentType("application/json").content(DEPARTMENT)).andExpect(status().isForbidden());
    }
    @Test void ordinaryAuthenticatedUserCanReadOnlyOwnProfile()throws Exception{
        mvc.perform(get("/api/hr/staff/me").with(user("cashier").roles("CASHIER"))).andExpect(status().isOk());
        mvc.perform(get("/api/hr/staff").with(user("cashier").roles("CASHIER"))).andExpect(status().isForbidden());
    }
    @Test void unauthenticatedAccessIsDenied()throws Exception{mvc.perform(get("/api/hr/staff/me")).andExpect(status().isForbidden());}
}
