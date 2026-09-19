package com.casino.casinoerp;
import com.casino.casinoerp.config.*;
import com.casino.casinoerp.controller.AccountsController;
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
@WebMvcTest(AccountsController.class) @Import({SecurityConfig.class,JwtAuthenticationFilter.class})
class AccountsSecurityTests {
 @Autowired MockMvc mvc;@MockitoBean AccountsService service;@MockitoBean JwtService jwt;
 @ParameterizedTest @ValueSource(strings={"STORE_MANAGER","ACCOUNTANT_HEAD","ACCOUNTS_MANAGER","DIRECTOR","SUPER_ADMIN","ADMIN","ACCOUNTS","MANAGER","RECEPTIONIST","CASHIER","DEALER","PIT_SUPERVISOR"})
 void accountBoundary(String role)throws Exception {
  boolean allowed=java.util.Set.of("STORE_MANAGER","ACCOUNTANT_HEAD","ACCOUNTS_MANAGER","DIRECTOR").contains(role);
  for(String path:java.util.List.of("context","bills","bills/00000000-0000-0000-0000-000000000001","bills/00000000-0000-0000-0000-000000000001/history/evidence"))mvc.perform(get("/api/accounts/"+path).with(user("actor").roles(role))).andExpect(allowed?status().isOk():status().isForbidden());
 }
 @org.junit.jupiter.api.Test void clientAuthorityFieldsAreRejected()throws Exception {
  for(String field:java.util.List.of("status","verifiedBy","approvedBy"))mvc.perform(post("/api/accounts/bills/00000000-0000-0000-0000-000000000001/approve").with(user("director").roles("DIRECTOR")).contentType("application/json").content("{\"idempotencyKey\":\"key\",\"expectedVersion\":1,\""+field+"\":\"forged\"}")).andExpect(status().isBadRequest());
 }
 @ParameterizedTest @ValueSource(strings={"STORE_MANAGER","ACCOUNTANT_HEAD","ACCOUNTS_MANAGER"})
 void newRolesDoNotGainOperationalAccess(String role)throws Exception {
  for(String path:java.util.List.of("/api/store/items","/api/audit-logs","/api/users","/api/dashboard/management"))mvc.perform(get(path).with(user("actor").roles(role))).andExpect(status().isForbidden());
 }
 @MockitoBean com.casino.casinoerp.repository.UserRepository users;
 @ParameterizedTest @ValueSource(strings={"STORE_MANAGER","ACCOUNTANT_HEAD","ACCOUNTS_MANAGER"})
 void newRoleTokenRequiresCurrentActiveAccount(String role)throws Exception {
  var actor=new com.casino.casinoerp.entity.User();actor.setId(java.util.UUID.randomUUID());actor.setUsername("accounts-actor");actor.setRole(role);actor.setStatus("ACTIVE");
  org.mockito.Mockito.when(users.findByUsername("accounts-actor")).thenReturn(actor);
  org.mockito.Mockito.when(jwt.extractUsername("accounts-token")).thenReturn("accounts-actor");org.mockito.Mockito.when(jwt.extractRole("accounts-token")).thenReturn(role);
  mvc.perform(get("/api/accounts/context").header("Authorization","Bearer accounts-token")).andExpect(status().isOk());
  actor.setRole("CASHIER");mvc.perform(get("/api/accounts/context").header("Authorization","Bearer accounts-token")).andExpect(status().isUnauthorized());
  actor.setRole(role);actor.setStatus("INACTIVE");mvc.perform(get("/api/accounts/context").header("Authorization","Bearer accounts-token")).andExpect(status().isUnauthorized());
 }
}
