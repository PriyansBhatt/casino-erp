package com.casino.casinoerp;
import com.casino.casinoerp.config.*;
import com.casino.casinoerp.controller.AuditLogController;
import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.service.*;
import com.casino.casinoerp.repository.AuditLogReadRepository;
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
@WebMvcTest(AuditLogController.class)
@Import({SecurityConfig.class,JwtAuthenticationFilter.class,AuditLogReadService.class,RolePermissionService.class})
class AuditLogSecurityTests {
 @Autowired MockMvc mvc;
 @MockitoBean AuditLogReadRepository reads;
 @MockitoBean CurrentUserRoleService roles;
 @MockitoBean JwtService jwt;
 @ParameterizedTest @ValueSource(strings={"SUPER_ADMIN","DIRECTOR"})
 void managementReadsBoundedPage(String role)throws Exception {
  when(roles.getCurrentUserRole()).thenReturn(role);
  when(reads.read(0,50,null,null,null,null,null,null,null)).thenReturn(new AuditLogPage(List.of(),0,50,false));
  mvc.perform(get("/api/audit-logs").with(user("actor").roles(role))).andExpect(status().isOk()).andExpect(jsonPath("$.data.size").value(50)).andExpect(jsonPath("$.data.items").isEmpty());
  verify(reads).read(0,50,null,null,null,null,null,null,null);
 }
 @ParameterizedTest @ValueSource(strings={"COMPLIANCE_OFFICER","SURVEILLANCE_OFFICER","CASHIER","RECEPTIONIST","DEALER","PIT_SUPERVISOR","ADMIN","MANAGER"})
 void otherRolesDenied(String role)throws Exception {
  mvc.perform(get("/api/audit-logs").with(user("actor").roles(role))).andExpect(status().isForbidden());verifyNoInteractions(reads);
 }
 @Test void anonymousDenied()throws Exception { mvc.perform(get("/api/audit-logs")).andExpect(status().isForbidden());verifyNoInteractions(reads); }
 @Test void invalidFiltersAreControlled()throws Exception {
  when(roles.getCurrentUserRole()).thenReturn("DIRECTOR");
  for(String query:List.of("page=-1","page=2147483648","size=0","size=101","size=bad","actorId=secret-marker","businessDate=bad","from=bad","to=bad","from=2026-09-02T09:00:00&to=2026-09-01T09:00:00"))
   mvc.perform(get("/api/audit-logs?"+query).with(user("actor").roles("DIRECTOR"))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
  verifyNoInteractions(reads);
 }
 @Test void noMutationOrLegacyUnboundedHandlers()throws Exception {
  for(String path:List.of("/api/audit-logs","/api/audit-logs/any"))
   for(var request:List.of(post(path),patch(path),put(path),delete(path)))mvc.perform(request.with(user("root").roles("SUPER_ADMIN"))).andExpect(status().isForbidden());
  for(String path:List.of("/user/00000000-0000-0000-0000-000000000001","/action/TEST","/module/TEST","/business-date/2026-09-01","/export","/00000000-0000-0000-0000-000000000001"))
   mvc.perform(get("/api/audit-logs"+path).with(user("root").roles("SUPER_ADMIN"))).andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false));
  verifyNoInteractions(reads);
 }
}
