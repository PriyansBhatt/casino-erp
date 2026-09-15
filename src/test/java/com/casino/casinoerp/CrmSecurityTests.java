package com.casino.casinoerp;
import com.casino.casinoerp.config.*;
import com.casino.casinoerp.controller.CrmController;
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
@WebMvcTest(CrmController.class) @Import({SecurityConfig.class,JwtAuthenticationFilter.class})
class CrmSecurityTests {
 @Autowired MockMvc mvc; @MockitoBean CrmService service; @MockitoBean JwtService jwt;
 static final String BODY="""
 {"customerId":"00000000-0000-0000-0000-000000000001","serviceType":"GIFT","description":"Gift","serviceAt":"2026-01-01T10:00:00","classification":"COMPLIMENTARY","cost":null,"idempotencyKey":"key"}
 """;
 @ParameterizedTest @ValueSource(strings={"SUPER_ADMIN","DIRECTOR","ADMIN","RECEPTIONIST","MANAGER","CASHIER","DEALER","PIT_SUPERVISOR"}) void roleMatrix(String role)throws Exception {
 mvc.perform(get("/api/crm/records").with(user(role).roles(role))).andExpect(role.equals("SUPER_ADMIN")||role.equals("DIRECTOR")?status().isOk():status().isForbidden());
 mvc.perform(post("/api/crm/services").with(user(role).roles(role)).contentType("application/json").content(BODY)).andExpect(role.equals("SUPER_ADMIN")?status().isCreated():status().isForbidden());
 mvc.perform(patch("/api/crm/transport/00000000-0000-0000-0000-000000000001/status").with(user(role).roles(role)).contentType("application/json").content("{\"status\":\"COMPLETED\",\"expectedVersion\":0}")).andExpect(role.equals("SUPER_ADMIN")?status().isOk():status().isForbidden());
 }
 @ParameterizedTest @ValueSource(strings={"-1","100000000000000000","0.001"}) void invalidCosts(String cost)throws Exception{postBody(BODY.replace("\"cost\":null","\"cost\":"+cost),400);}
 @Test void unknownActorAndTypeRejected()throws Exception{postBody(BODY.replace("\"description\":","\"recordedBy\":\"spoof\",\"description\":"),400);postBody(BODY.replace("GIFT","BONUS"),400);postBody(BODY.replace("2026-01-01T10:00:00","bad"),400);}
 @Test void zeroAndUnknownAreValid()throws Exception{postBody(BODY,201);postBody(BODY.replace("\"cost\":null","\"cost\":0"),201);}
 @Test void transportValidationAndSpoofProtection()throws Exception {
 String body="""
 {"customerId":"00000000-0000-0000-0000-000000000001","transportType":"AIRPORT_PICKUP","pickup":"Airport","destination":"Hotel","scheduledAt":"2027-01-01T10:00:00","cost":null,"idempotencyKey":"transport"}
 """;
 mvc.perform(post("/api/crm/transport").with(user("sa").roles("SUPER_ADMIN")).contentType("application/json").content(body)).andExpect(status().isCreated());
 for(String invalid:java.util.List.of(body.replace("AIRPORT_PICKUP","FLEET"),body.replace("Airport",""),body.replace("2027-01-01T10:00:00","invalid"),body.replace("\"cost\":null","\"cost\":-1"),body.replace("\"pickup\":","\"recordedBy\":\"spoof\",\"pickup\":")))mvc.perform(post("/api/crm/transport").with(user("sa").roles("SUPER_ADMIN")).contentType("application/json").content(invalid)).andExpect(status().isBadRequest());
 }
 @Test void unauthenticatedAccessDenied()throws Exception{mvc.perform(get("/api/crm/records")).andExpect(status().is4xxClientError());mvc.perform(post("/api/crm/services").contentType("application/json").content(BODY)).andExpect(status().is4xxClientError());}
 private void postBody(String body,int expected)throws Exception{mvc.perform(post("/api/crm/services").with(user("sa").roles("SUPER_ADMIN")).contentType("application/json").content(body)).andExpect(status().is(expected));}
}
