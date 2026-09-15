package com.casino.casinoerp;
import com.casino.casinoerp.config.*;
import com.casino.casinoerp.controller.MachineController;
import com.casino.casinoerp.service.*;
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
@WebMvcTest(MachineController.class)
@Import({SecurityConfig.class,JwtAuthenticationFilter.class})
class MachineSecurityTests {
 @Autowired MockMvc mvc; @MockitoBean MachineService service; @MockitoBean JwtService jwt;
 static final String ID="00000000-0000-0000-0000-000000000001";
 @ParameterizedTest @ValueSource(strings={"SUPER_ADMIN","DIRECTOR","CASHIER","PIT_SUPERVISOR","DEALER","RECEPTIONIST","ADMIN"})
 void roleMatrix(String role)throws Exception {
  mvc.perform(get("/api/machines").with(user("actor").roles(role))).andExpect(role.equals("SUPER_ADMIN")||role.equals("DIRECTOR")?status().isOk():status().isForbidden());
  String body="{\"customerId\":\""+ID+"\",\"customerSessionId\":\""+ID+"\",\"expectedBusinessDate\":\"2026-09-15\",\"idempotencyKey\":\"key\"}";
  mvc.perform(post("/api/machines/"+ID+"/plays").with(user("actor").roles(role)).contentType("application/json").content(body)).andExpect(role.equals("SUPER_ADMIN")?status().isOk():status().isForbidden());
 }
 @org.junit.jupiter.api.Test void invalidRequestIs400()throws Exception {
  mvc.perform(post("/api/machines").with(user("actor").roles("SUPER_ADMIN")).contentType("application/json").content("{\"machineCode\":\"x\",\"displayName\":\" \",\"machineType\":\"SLOT\"}")).andExpect(status().isBadRequest());
 }
}
