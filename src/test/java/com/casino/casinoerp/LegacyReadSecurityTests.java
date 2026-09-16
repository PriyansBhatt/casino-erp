package com.casino.casinoerp;
import com.casino.casinoerp.config.*;
import com.casino.casinoerp.controller.*;
import com.casino.casinoerp.service.*;
import com.casino.casinoerp.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
@WebMvcTest({DashboardController.class,DailyReportController.class,BusinessDateSummaryController.class,CasinoDashboardController.class,
 AlertDashboardController.class,HighValueAlertController.class,SuspiciousAlertController.class,CustomerCheckInController.class})
@Import({SecurityConfig.class,JwtAuthenticationFilter.class})
class LegacyReadSecurityTests {
 @Autowired MockMvc mvc;
 @MockitoBean JwtService jwt;
 @MockitoBean CustomerRepository customers;@MockitoBean ChipBuyInRepository buyins;@MockitoBean ChipCashOutRepository cashouts;
 @MockitoBean CustomerSessionRepository sessions;@MockitoBean CustomerCheckInRepository checkins;@MockitoBean CustomerWalletRepository wallets;
 @MockitoBean PitTableRepository tables;@MockitoBean PitTableTransactionRepository transactions;@MockitoBean PitTableReconciliationService pit;
 @MockitoBean WalletTransactionService walletService;@MockitoBean CustomerServiceRecordService serviceRecords;@MockitoBean CustomerSessionService sessionService;
 @MockitoBean BusinessDateService dates;@MockitoBean CustomerCheckInService checkinService;
 static final List<String> MANAGEMENT=List.of("/api/dashboard/cashier-activity","/api/dashboard/summary","/api/dashboard/active-sessions",
 "/api/dashboard/casino-position","/api/dashboard/high-value-alerts","/api/dashboard/pit-summary","/api/dashboard/table-results",
 "/api/dashboard/pit-performance","/api/dashboard/overall-position","/api/daily-report/2026-09-15","/api/daily-report/2026-09-15/customers",
 "/api/business-date-summary/2026-09-15","/api/casino-dashboard/2026-09-15");
 static final List<String> ALERTS=List.of("/api/alert-dashboard/2026-09-15","/api/high-value-alerts/2026-09-15","/api/suspicious-alerts/2026-09-15");
 @ParameterizedTest @ValueSource(strings={"SUPER_ADMIN","DIRECTOR","RECEPTIONIST","CASHIER","PIT_SUPERVISOR","DEALER","MANAGER","ADMIN","COMPLIANCE_OFFICER","SURVEILLANCE_OFFICER"})
 void explicitRoleMatrix(String role)throws Exception {
  boolean management=role.equals("SUPER_ADMIN")||role.equals("DIRECTOR");
  for(String path:MANAGEMENT)mvc.perform(get(path).with(user(role).roles(role))).andExpect(management?status().isOk():status().isForbidden());
  for(String path:ALERTS)mvc.perform(get(path).with(user(role).roles(role))).andExpect(management||role.equals("SURVEILLANCE_OFFICER")?status().isOk():status().isForbidden());
  mvc.perform(get("/api/checkins").with(user(role).roles(role))).andExpect(management||role.equals("RECEPTIONIST")?status().isOk():status().isForbidden());
 }
 @Test void unauthenticatedCannotReadAnyLegacyProjection()throws Exception {var paths=new ArrayList<>(MANAGEMENT);paths.addAll(ALERTS);paths.add("/api/checkins");for(String path:paths)mvc.perform(get(path)).andExpect(status().is4xxClientError());}
}
