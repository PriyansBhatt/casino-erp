package com.casino.casinoerp;

import com.casino.casinoerp.config.JwtAuthenticationFilter;
import com.casino.casinoerp.config.SecurityConfig;
import com.casino.casinoerp.controller.ChipBuyInController;
import com.casino.casinoerp.controller.ChipCashOutController;
import com.casino.casinoerp.controller.CustomerReportController;
import com.casino.casinoerp.controller.CustomerServiceRecordController;
import com.casino.casinoerp.controller.CustomerServiceReportController;
import com.casino.casinoerp.controller.CustomerTierController;
import com.casino.casinoerp.controller.CustomerValueReportController;
import com.casino.casinoerp.controller.CustomerWalletController;
import com.casino.casinoerp.controller.DailyCustomerValueController;
import com.casino.casinoerp.controller.WalletTransactionController;
import com.casino.casinoerp.service.ChipBuyInService;
import com.casino.casinoerp.service.ChipCashOutService;
import com.casino.casinoerp.service.CustomerServiceRecordService;
import com.casino.casinoerp.service.CustomerWalletService;
import com.casino.casinoerp.service.JwtService;
import com.casino.casinoerp.service.WalletTransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        CustomerWalletController.class,
        WalletTransactionController.class,
        ChipBuyInController.class,
        ChipCashOutController.class,
        CustomerReportController.class,
        CustomerTierController.class,
        CustomerValueReportController.class,
        DailyCustomerValueController.class,
        CustomerServiceRecordController.class,
        CustomerServiceReportController.class
})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class CustomerFinancialSecurityTests {

    private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerWalletService customerWalletService;

    @MockitoBean
    private WalletTransactionService walletTransactionService;

    @MockitoBean
    private ChipBuyInService chipBuyInService;

    @MockitoBean
    private ChipCashOutService chipCashOutService;

    @MockitoBean
    private CustomerServiceRecordService customerServiceRecordService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void receptionistCannotAccessCustomerFinancialValueOrHistoryApis() throws Exception {
        assertForbiddenForReceptionist("/api/wallets/customer/" + CUSTOMER_ID);
        assertForbiddenForReceptionist("/api/wallet-transactions/customer/" + CUSTOMER_ID);
        assertForbiddenForReceptionist("/api/buyins");
        assertForbiddenForReceptionist("/api/cashouts");
        assertForbiddenForReceptionist("/api/customer-report/" + CUSTOMER_ID);
        assertForbiddenForReceptionist("/api/customer-tier/" + CUSTOMER_ID);
        assertForbiddenForReceptionist(
                "/api/customer-value-report/customer/" + CUSTOMER_ID + "/business-date/2026-08-08"
        );
        assertForbiddenForReceptionist("/api/daily-customer-value/2026-08-08");
        assertForbiddenForReceptionist("/api/customer-services/customer/" + CUSTOMER_ID);
        assertForbiddenForReceptionist("/api/customer-service-report/customer/" + CUSTOMER_ID);
    }

    @Test
    void directorCanAccessPrivilegedCustomerFinancialValueAndHistoryApis() throws Exception {
        stubEmptyReportData();
        assertAllowedForRole("DIRECTOR");
    }

    @Test
    void superAdminCanAccessPrivilegedCustomerFinancialValueAndHistoryApis() throws Exception {
        stubEmptyReportData();
        assertAllowedForRole("SUPER_ADMIN");
    }

    @Test
    void cashierRetainsOperationalFinancialReadAccess() throws Exception {
        when(walletTransactionService.getByCustomer(CUSTOMER_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/wallet-transactions/customer/{customerId}", CUSTOMER_ID)
                        .with(user("cashier").roles("CASHIER")))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedCustomerFinancialRequestIsDenied() throws Exception {
        mockMvc.perform(get("/api/customer-report/{customerId}", CUSTOMER_ID))
                .andExpect(status().isForbidden());
    }

    private void assertForbiddenForReceptionist(String path) throws Exception {
        mockMvc.perform(get(path).with(user("reception").roles("RECEPTIONIST")))
                .andExpect(status().isForbidden());
    }

    private void assertAllowedForRole(String role) throws Exception {
        String[] paths = {
                "/api/wallets",
                "/api/wallet-transactions/customer/" + CUSTOMER_ID,
                "/api/buyins",
                "/api/cashouts",
                "/api/customer-report/" + CUSTOMER_ID,
                "/api/customer-tier/" + CUSTOMER_ID,
                "/api/customer-value-report/customer/" + CUSTOMER_ID + "/business-date/2026-08-08",
                "/api/daily-customer-value/2026-08-08",
                "/api/customer-services/customer/" + CUSTOMER_ID,
                "/api/customer-service-report/customer/" + CUSTOMER_ID
        };

        for (String path : paths) {
            mockMvc.perform(get(path).with(user(role.toLowerCase()).roles(role)))
                    .andExpect(status().isOk());
        }
    }

    private void stubEmptyReportData() {
        when(walletTransactionService.getByCustomer(CUSTOMER_ID)).thenReturn(List.of());
        when(customerServiceRecordService.getByCustomer(CUSTOMER_ID)).thenReturn(List.of());
    }
}
