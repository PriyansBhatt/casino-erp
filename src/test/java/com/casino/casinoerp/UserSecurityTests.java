package com.casino.casinoerp;
import com.casino.casinoerp.config.*;
import com.casino.casinoerp.controller.UserController;
import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
@WebMvcTest(UserController.class)
@Import({SecurityConfig.class,JwtAuthenticationFilter.class})
class UserSecurityTests {
 @Autowired MockMvc mvc; @MockitoBean UserService service; @MockitoBean JwtService jwtService;
 UUID id=UUID.randomUUID();String create="{\"username\":\"new\",\"fullName\":\"New User\",\"role\":\"CASHIER\",\"password\":\"secret-value\"}";
 @ParameterizedTest @ValueSource(strings={"DIRECTOR","CASHIER","RECEPTIONIST","PIT_SUPERVISOR","DEALER","MANAGER","COMPLIANCE_OFFICER","SURVEILLANCE_OFFICER","ADMIN"})
 void allNonAdminsDenied(String role)throws Exception{
  var actor=user("operator").roles(role);
  mvc.perform(get("/api/users").with(actor)).andExpect(status().isForbidden());
  mvc.perform(post("/api/users").with(actor).contentType("application/json").content(create)).andExpect(status().isForbidden());
  mvc.perform(patch("/api/users/{id}/role",id).with(actor).contentType("application/json").content("{\"role\":\"DEALER\",\"expectedRole\":\"CASHIER\"}")).andExpect(status().isForbidden());
  mvc.perform(patch("/api/users/{id}/status",id).with(actor).contentType("application/json").content("{\"status\":\"INACTIVE\",\"expectedStatus\":\"ACTIVE\"}")).andExpect(status().isForbidden());
  mvc.perform(post("/api/users/{id}/password",id).with(actor).contentType("application/json").content("{\"password\":\"replacement\"}")).andExpect(status().isForbidden());
  verifyNoInteractions(service);
 }
 @Test void adminDirectoryAndMutationContractsAreSafe()throws Exception{
  var response=new UserResponse(id,"new","New User",null,"ACTIVE","CASHIER",null);
  when(service.getAllUsers()).thenReturn(List.of(response));when(service.create(any())).thenReturn(response);when(service.changeRole(eq(id),any())).thenReturn(response);when(service.changeStatus(eq(id),any())).thenReturn(response);when(service.resetPassword(eq(id),any())).thenReturn(response);
  var actor=user("admin").roles("SUPER_ADMIN");
  mvc.perform(get("/api/users").with(actor)).andExpect(status().isOk()).andExpect(jsonPath("$[0].username").value("new")).andExpect(jsonPath("$[0].passwordHash").doesNotExist());
  mvc.perform(post("/api/users").with(actor).contentType("application/json").content(create)).andExpect(status().isCreated()).andExpect(jsonPath("$.data.password").doesNotExist()).andExpect(jsonPath("$.data.passwordHash").doesNotExist());
  mvc.perform(patch("/api/users/{id}/role",id).with(actor).contentType("application/json").content("{\"role\":\"DEALER\",\"expectedRole\":\"CASHIER\"}")).andExpect(status().isOk());
  mvc.perform(patch("/api/users/{id}/status",id).with(actor).contentType("application/json").content("{\"status\":\"INACTIVE\",\"expectedStatus\":\"ACTIVE\"}")).andExpect(status().isOk());
  mvc.perform(post("/api/users/{id}/password",id).with(actor).contentType("application/json").content("{\"password\":\"replacement\"}")).andExpect(status().isOk());
 }
 @Test void malformedAndMissingFieldsAreControlled()throws Exception{
  for(String body:List.of("{}","{","{\"username\":\" ","{\"password\":\"short\"}"))mvc.perform(post("/api/users").with(user("admin").roles("SUPER_ADMIN")).contentType("application/json").content(body)).andExpect(status().isBadRequest());
  verifyNoInteractions(service);
 }
 @Test void noDeleteHandler()throws Exception{mvc.perform(delete("/api/users/{id}",id).with(user("admin").roles("SUPER_ADMIN"))).andExpect(status().is4xxClientError());}

 @Test void passwordRequestIsRedactedInFrameworkLogs(org.springframework.boot.test.system.CapturedOutput output)throws Exception{
  doThrow(new IllegalArgumentException("Password does not satisfy the required length.")).when(service).resetPassword(eq(id),any());
  mvc.perform(post("/api/users/{id}/password",id).with(user("admin").roles("SUPER_ADMIN")).contentType("application/json").content("{\"password\":\"a1-sensitive-probe\"}"))
    .andExpect(status().isBadRequest()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("a1-sensitive-probe"))));
  org.assertj.core.api.Assertions.assertThat(output.getAll()).doesNotContain("a1-sensitive-probe");
 }

 @Test void malformedCredentialsNeverReachResponsesOrConfiguredLogs(org.springframework.boot.test.system.CapturedOutput output)throws Exception {
  String marker="A1MalformedSecretRegression";
  for(String path:List.of("/api/users", "/api/users/"+id+"/password")) {
   for(String body:List.of("{\"password\":"+marker+"}", "{\"password\":{\"secret\":\""+marker+"\"}}")) {
    mvc.perform(post(path).with(user("admin").roles("SUPER_ADMIN")).contentType("application/json").content(body))
      .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
      .andExpect(jsonPath("$.message").value("Invalid request body or parameter."))
      .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(marker))));
   }
  }
  mvc.perform(post("/api/users/"+marker+"/password").with(user("admin").roles("SUPER_ADMIN")).contentType("application/json").content("{\"password\":\"valid-password\"}"))
    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Invalid request body or parameter."));
  verifyNoInteractions(service);
  org.assertj.core.api.Assertions.assertThat(output.getAll()).doesNotContain(marker);
 }
}
