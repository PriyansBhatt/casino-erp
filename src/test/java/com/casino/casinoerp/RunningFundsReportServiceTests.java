package com.casino.casinoerp;

import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.repository.*;
import com.casino.casinoerp.security.Role;
import com.casino.casinoerp.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RunningFundsReportServiceTests {
    private final BusinessDateRepository dates = mock(BusinessDateRepository.class);
    private final BusinessDateService dateService = mock(BusinessDateService.class);
    private final ChipBuyInRepository buyIns = mock(ChipBuyInRepository.class);
    private final ChipCashOutRepository cashOuts = mock(ChipCashOutRepository.class);
    private final LosingReturnRepository losingReturns = mock(LosingReturnRepository.class);
    private final VerifiedGamingResultRepository gaming = mock(VerifiedGamingResultRepository.class);
    private final CashierReconciliationRepository reconciliations = mock(CashierReconciliationRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final SessionFinancialPositionService positions = mock(SessionFinancialPositionService.class);
    private final CurrentUserRoleService roles = mock(CurrentUserRoleService.class);
    private final RunningFundsReportService service = new RunningFundsReportService(
            dates, dateService, buyIns, cashOuts, losingReturns, gaming, reconciliations,
            users, positions, roles, new RolePermissionService());
    private final LocalDate date = LocalDate.of(2026, 8, 8);

    @BeforeEach
    void setUp() {
        when(roles.getCurrentRole()).thenReturn(Optional.of(Role.DIRECTOR));
        BusinessDate businessDate = new BusinessDate();
        businessDate.setBusinessDate(date);
        businessDate.setStatus("OPEN");
        when(dates.findByBusinessDate(date)).thenReturn(Optional.of(businessDate));
        when(buyIns.findByBusinessDate(date)).thenReturn(List.of());
        when(cashOuts.findByBusinessDate(date)).thenReturn(List.of());
        when(losingReturns.findByBusinessDate(date)).thenReturn(List.of());
        when(gaming.findByBusinessDate(date)).thenReturn(List.of());
        when(reconciliations.findByBusinessDateOrderBySubmittedAtDesc(date)).thenReturn(List.of());
        when(users.findAll()).thenReturn(List.of());
        when(positions.getOutstandingPosition(date)).thenReturn(BigDecimal.ZERO);
    }

    @Test
    void aggregatesAuthoritativeCashGamingAndReconciliationValues() {
        UUID cashierId = UUID.randomUUID();
        when(buyIns.findByBusinessDate(date)).thenReturn(List.of(buyIn("100000", cashierId)));
        when(cashOuts.findByBusinessDate(date)).thenReturn(List.of(cashOut("25000", cashierId)));
        when(losingReturns.findByBusinessDate(date)).thenReturn(List.of(losingReturn("5000", cashierId)));
        when(gaming.findByBusinessDate(date)).thenReturn(List.of(result("20000", VerifiedGamingResultType.WIN), result("50000", VerifiedGamingResultType.LOSS)));
        when(positions.getOutstandingPosition(date)).thenReturn(new BigDecimal("45000"));
        CashierReconciliation reconciliation = reconciliation(cashierId, "SUBMITTED", "100");
        when(reconciliations.findByBusinessDateOrderBySubmittedAtDesc(date)).thenReturn(List.of(reconciliation));
        User cashier = cashier(cashierId);
        when(users.findAllById(any())).thenReturn(List.of(cashier));
        when(users.findAll()).thenReturn(List.of(cashier));

        var report = service.getReport(date);

        assertThat(report.buyInReceived()).isEqualByComparingTo("100000");
        assertThat(report.cashOutPaid()).isEqualByComparingTo("25000");
        assertThat(report.losingReturnPaid()).isEqualByComparingTo("5000");
        assertThat(report.netCustomerCashMovement()).isEqualByComparingTo("70000");
        assertThat(report.verifiedGamingWins()).isEqualByComparingTo("20000");
        assertThat(report.verifiedGamingLosses()).isEqualByComparingTo("50000");
        assertThat(report.casinoGamingNet()).isEqualByComparingTo("30000");
        assertThat(report.outstandingCustomerChipPosition()).isEqualByComparingTo("45000");
        assertThat(report.submittedCashiers()).isOne();
        assertThat(report.unresolvedCashiers()).isZero();
        assertThat(report.aggregateSubmittedVariance()).isEqualByComparingTo("100");
    }

    @Test
    void explicitBusinessDateIsIsolatedAndEmptyDateReturnsZeroTotals() {
        var report = service.getReport(date);

        assertThat(report.buyInReceived()).isZero();
        assertThat(report.cashOutPaid()).isZero();
        assertThat(report.losingReturnPaid()).isZero();
        assertThat(report.netCustomerCashMovement()).isZero();
        assertThat(report.verifiedGamingWins()).isZero();
        assertThat(report.verifiedGamingLosses()).isZero();
        verify(buyIns).findByBusinessDate(date);
        verify(cashOuts).findByBusinessDate(date);
        verify(losingReturns).findByBusinessDate(date);
        verify(gaming).findByBusinessDate(date);
        verify(buyIns, never()).findAll();
    }

    @Test
    void reopenedReconciliationIsReportedAsUnresolvedAndExcludedFromSubmittedVariance() {
        UUID cashierId = UUID.randomUUID();
        CashierReconciliation reopened = reconciliation(cashierId, "REOPENED", "250");
        User cashier = cashier(cashierId);
        when(reconciliations.findByBusinessDateOrderBySubmittedAtDesc(date)).thenReturn(List.of(reopened));
        when(users.findAllById(any())).thenReturn(List.of(cashier));
        when(users.findAll()).thenReturn(List.of(cashier));

        var report = service.getReport(date);

        assertThat(report.submittedCashiers()).isZero();
        assertThat(report.reopenedCashiers()).isOne();
        assertThat(report.unresolvedCashiers()).isOne();
        assertThat(report.aggregateSubmittedVariance()).isZero();
    }

    @Test
    void operationalRoleCannotReadManagementReport() {
        when(roles.getCurrentRole()).thenReturn(Optional.of(Role.CASHIER));
        assertThatThrownBy(() -> service.getReport(date)).hasMessageContaining("restricted to management");
        verifyNoInteractions(dates);
    }

    private ChipBuyIn buyIn(String amount, UUID actor) {
        ChipBuyIn value = new ChipBuyIn(); value.setAmountReceived(new BigDecimal(amount)); value.setCreatedBy(actor); return value;
    }
    private ChipCashOut cashOut(String amount, UUID actor) {
        ChipCashOut value = new ChipCashOut(); value.setCashPaid(new BigDecimal(amount)); value.setCreatedBy(actor); return value;
    }
    private LosingReturn losingReturn(String amount, UUID actor) {
        LosingReturn value = new LosingReturn(); value.setAmountPaid(new BigDecimal(amount)); value.setCreatedBy(actor); return value;
    }
    private VerifiedGamingResult result(String amount, VerifiedGamingResultType type) {
        VerifiedGamingResult value = new VerifiedGamingResult(); value.setAmount(new BigDecimal(amount)); value.setResultType(type); return value;
    }
    private CashierReconciliation reconciliation(UUID cashierId, String lifecycle, String variance) {
        CashierReconciliation value = new CashierReconciliation(); value.setId(UUID.randomUUID()); value.setCashierUserId(cashierId);
        value.setLifecycleStatus(lifecycle); value.setStatus("BALANCED"); value.setVariance(new BigDecimal(variance));
        value.setExpectedClosingCash(BigDecimal.TEN); value.setActualClosingCash(BigDecimal.TEN); return value;
    }
    private User cashier(UUID id) {
        User value = new User(); value.setId(id); value.setUsername("cashier"); value.setFullName("Cashier");
        value.setRole("CASHIER"); value.setStatus("ACTIVE"); return value;
    }
}
