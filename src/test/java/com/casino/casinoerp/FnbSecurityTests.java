package com.casino.casinoerp;
import com.casino.casinoerp.config.*;
import com.casino.casinoerp.controller.FnbController;
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
@WebMvcTest(FnbController.class) @Import({SecurityConfig.class,JwtAuthenticationFilter.class})
class FnbSecurityTests {
 @Autowired MockMvc mvc; @MockitoBean FnbService service; @MockitoBean JwtService jwt;
 static final String BODY="""
 {"customerId":"00000000-0000-0000-0000-000000000001","expectedBusinessDate":"2026-09-15","type":"FOOD","item":"Rice","quantity":2,"location":"Floor","idempotencyKey":"key"}
 """;
 @ParameterizedTest @ValueSource(strings={"SUPER_ADMIN","DIRECTOR","ADMIN","RECEPTIONIST","MANAGER","CASHIER","DEALER","PIT_SUPERVISOR"}) void roleMatrix(String role)throws Exception {
 mvc.perform(get("/api/fnb/requests").with(user(role).roles(role))).andExpect(role.equals("SUPER_ADMIN")||role.equals("DIRECTOR")?status().isOk():status().isForbidden());
 mvc.perform(post("/api/fnb/requests").with(user(role).roles(role)).contentType("application/json").content(BODY)).andExpect(role.equals("SUPER_ADMIN")?status().isCreated():status().isForbidden());
 mvc.perform(patch("/api/fnb/requests/00000000-0000-0000-0000-000000000001/status").with(user(role).roles(role)).contentType("application/json").content("{\"status\":\"PREPARING\",\"expectedVersion\":0}")).andExpect(role.equals("SUPER_ADMIN")?status().isOk():status().isForbidden());
 }
 @ParameterizedTest @ValueSource(strings={"1.5","\"2\"","-1","0","null","2147483648","999999999999999999999999","true","[]","{}"})
 void invalidQuantity(String quantity)throws Exception { postBody(BODY.replace("\"quantity\":2","\"quantity\":"+quantity),400); }
 @Test void foodAndBeverageValid()throws Exception { postBody(BODY,201);postBody(BODY.replace("FOOD","BEVERAGE"),201); }
 @Test void spoofedAuthorityRejected()throws Exception {
  for(String field:java.util.List.of("requestedBy","price","department","status"))postBody(BODY.replace("\"item\":","\""+field+"\":\"spoof\",\"item\":"),400);
 }
 @Test void invalidCustomerAndDate()throws Exception { postBody(BODY.replace("2026-09-15","bad"),400);postBody(BODY.replace("00000000-0000-0000-0000-000000000001","bad"),400); }
 @Test void unauthenticatedDenied()throws Exception { mvc.perform(get("/api/fnb/requests")).andExpect(status().is4xxClientError()); }
 private void postBody(String body,int expected)throws Exception { mvc.perform(post("/api/fnb/requests").with(user("sa").roles("SUPER_ADMIN")).contentType("application/json").content(body)).andExpect(status().is(expected)); }
}
