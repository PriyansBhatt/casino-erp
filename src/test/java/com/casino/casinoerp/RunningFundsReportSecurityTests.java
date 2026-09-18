package com.casino.casinoerp;
import com.casino.casinoerp.config.*;
import com.casino.casinoerp.controller.RunningFundsReportController;
import com.casino.casinoerp.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.HttpMethod;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import java.time.LocalDate;

@WebMvcTest(RunningFundsReportController.class)
@Import({SecurityConfig.class,JwtAuthenticationFilter.class})
class RunningFundsReportSecurityTests {
 @Autowired MockMvc mvc;
 @MockitoBean RunningFundsReportService service;
 @MockitoBean JwtService jwtService;
 @ParameterizedTest @ValueSource(strings={"DIRECTOR","SUPER_ADMIN"})
 void reads(String role)throws Exception{
  for(String path:new String[]{"daily-operations","running-funds","reconciliations"})mvc.perform(get("/api/reports/"+path).param("businessDate","2026-09-02").with(user("manager").roles(role))).andExpect(status().isOk());
  verify(service,times(2)).getReport(LocalDate.of(2026,9,2));verify(service).reconciliations(LocalDate.of(2026,9,2),0,50);
 }
 @ParameterizedTest @ValueSource(strings={"ADMIN","AUDITOR","CASHIER","RECEPTIONIST","DEALER","PIT_SUPERVISOR","SURVEILLANCE_OFFICER","COMPLIANCE_OFFICER"})
 void denied(String role)throws Exception{
  for(String path:new String[]{"daily-operations","running-funds","reconciliations"})mvc.perform(get("/api/reports/"+path).with(user("other").roles(role))).andExpect(status().isForbidden());verifyNoInteractions(service);
 }
 @Test void mutationsDeniedEvenForSuperAdmin()throws Exception{
  for(HttpMethod method:new HttpMethod[]{HttpMethod.POST,HttpMethod.PUT,HttpMethod.PATCH,HttpMethod.DELETE})for(String path:new String[]{"daily-operations","reconciliations","running-funds"})mvc.perform(request(method,"/api/reports/"+path).with(user("root").roles("SUPER_ADMIN"))).andExpect(status().isForbidden());verifyNoInteractions(service);
 }
 @Test void invalidFiltersAndUnauthenticatedAreControlled()throws Exception{
  mvc.perform(get("/api/reports/daily-operations").param("businessDate","2026-02-30").with(user("director").roles("DIRECTOR"))).andExpect(status().isBadRequest());
  mvc.perform(get("/api/reports/reconciliations").param("page","bad").with(user("director").roles("DIRECTOR"))).andExpect(status().isBadRequest());
  mvc.perform(get("/api/reports/daily-operations")).andExpect(status().isForbidden());
 }
 @Test void numericOverflowAndControlledServiceFailures()throws Exception {
  for(String param:new String[]{"page","size"})mvc.perform(get("/api/reports/reconciliations").param(param,"2147483648").with(user("director").roles("DIRECTOR"))).andExpect(status().isBadRequest());
  when(service.reconciliations(null,-1,50)).thenThrow(new IllegalArgumentException("Invalid page"));
  mvc.perform(get("/api/reports/reconciliations").param("page","-1").with(user("director").roles("DIRECTOR"))).andExpect(status().isBadRequest());
  when(service.getReport(null)).thenThrow(new IllegalArgumentException("No Business Date is open"));
  mvc.perform(get("/api/reports/daily-operations").with(user("director").roles("DIRECTOR"))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
  when(service.getReport(LocalDate.of(2199,1,1))).thenThrow(new com.casino.casinoerp.exception.ResourceNotFoundException("Business Date not found"));
  mvc.perform(get("/api/reports/daily-operations?businessDate=2199-01-01").with(user("director").roles("DIRECTOR"))).andExpect(status().isNotFound());
 }
}
