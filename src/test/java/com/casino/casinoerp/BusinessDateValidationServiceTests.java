package com.casino.casinoerp;

import com.casino.casinoerp.dto.SessionFinancialPositionResponse;
import com.casino.casinoerp.entity.CashierReconciliation;
import com.casino.casinoerp.entity.ChipBuyIn;
import com.casino.casinoerp.entity.CustomerSession;
import com.casino.casinoerp.entity.LegacyCashActorResolution;
import com.casino.casinoerp.entity.PitTable;
import com.casino.casinoerp.entity.PitTableCustomerAssignment;
import com.casino.casinoerp.entity.PitTableCustomerAssignmentStatus;
import com.casino.casinoerp.entity.PitTableStaffAssignment;
import com.casino.casinoerp.entity.User;
import com.casino.casinoerp.repository.CashierReconciliationRepository;
import com.casino.casinoerp.repository.CashierOpeningBalanceRepository;
import com.casino.casinoerp.repository.ChipBuyInRepository;
import com.casino.casinoerp.repository.ChipCashOutRepository;
import com.casino.casinoerp.repository.CustomerSessionRepository;
import com.casino.casinoerp.repository.PitTableCustomerAssignmentRepository;
import com.casino.casinoerp.repository.PitTableRepository;
import com.casino.casinoerp.repository.PitTableStaffAssignmentRepository;
import com.casino.casinoerp.repository.ChipCustodyInventoryRepository;
import com.casino.casinoerp.repository.CustomerSessionCustodySummaryProjection;
import com.casino.casinoerp.repository.PitTableCustodySummaryProjection;
import com.casino.casinoerp.repository.UserRepository;
import com.casino.casinoerp.repository.LegacyCashActorResolutionRepository;
import com.casino.casinoerp.repository.LosingReturnRepository;
import com.casino.casinoerp.security.Role;
import com.casino.casinoerp.service.BusinessDateValidationService;
import com.casino.casinoerp.service.SessionFinancialPositionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BusinessDateValidationServiceTests {
    private final CustomerSessionRepository sessions = mock(CustomerSessionRepository.class);
    private final PitTableCustomerAssignmentRepository assignments =
            mock(PitTableCustomerAssignmentRepository.class);
    private final SessionFinancialPositionService positions = mock(SessionFinancialPositionService.class);
    private final PitTableRepository tables = mock(PitTableRepository.class);
    private final PitTableStaffAssignmentRepository staffAssignments =
            mock(PitTableStaffAssignmentRepository.class);
    private final ChipCustodyInventoryRepository custody = mock(ChipCustodyInventoryRepository.class);
    private final CashierReconciliationRepository reconciliations =
            mock(CashierReconciliationRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final CashierOpeningBalanceRepository openingBalances = mock(CashierOpeningBalanceRepository.class);
    private final ChipBuyInRepository buyIns = mock(ChipBuyInRepository.class);
    private final ChipCashOutRepository cashOuts = mock(ChipCashOutRepository.class);
    private final LosingReturnRepository losingReturns = mock(LosingReturnRepository.class);
    private final LegacyCashActorResolutionRepository legacyResolutions =
            mock(LegacyCashActorResolutionRepository.class);
    private final BusinessDateValidationService service = new BusinessDateValidationService(
            sessions, assignments, positions, tables, staffAssignments, custody, reconciliations, users,
            openingBalances, buyIns, cashOuts, losingReturns, legacyResolutions);
    private final LocalDate date = LocalDate.of(2026, 8, 8);

    @BeforeEach
    void settledBusinessDate() {
        when(sessions.findByBusinessDateOrderByEntryTimeAsc(date)).thenReturn(List.of());
        when(assignments.findByBusinessDateAndStatusOrderByJoinedAtAsc(
                date, PitTableCustomerAssignmentStatus.ACTIVE)).thenReturn(List.of());
        when(staffAssignments.findByBusinessDateAndEndedAtIsNullOrderByStartedAtAsc(date))
                .thenReturn(List.of());
        when(tables.findByBusinessDate(date)).thenReturn(List.of());
        when(reconciliations.findByBusinessDateOrderBySubmittedAtDesc(date)).thenReturn(List.of());
        when(users.findAll()).thenReturn(List.of());
        when(buyIns.findByBusinessDate(date)).thenReturn(List.of());
        when(cashOuts.findByBusinessDate(date)).thenReturn(List.of());
        when(losingReturns.findByBusinessDate(date)).thenReturn(List.of());
    }

    @Test
    void allSessionsSettledTablesClosedAndReconciliationsResolvedAllowsClose() {
        assertThat(service.validateCloseRequirements(date)).isEmpty();
    }

    @Test
    void positiveCustomerChipPositionBlocksClose() {
        CustomerSession session = openSession();
        stubPosition(session, new BigDecimal("5000"));

        assertThat(service.validateCloseRequirements(date))
                .anyMatch(error -> error.contains("positive chip positions"));
    }

    @Test
    void negativeCustomerChipPositionBlocksClose() {
        CustomerSession session = openSession();
        stubPosition(session, new BigDecimal("-500"));

        assertThat(service.validateCloseRequirements(date))
                .anyMatch(error -> error.contains("negative chip positions"));
    }

    @Test
    void activePitAssignmentBlocksClose() {
        CustomerSession session = openSession();
        stubPosition(session, BigDecimal.ZERO);
        when(assignments.findByBusinessDateAndStatusOrderByJoinedAtAsc(
                date, PitTableCustomerAssignmentStatus.ACTIVE))
                .thenReturn(List.of(new PitTableCustomerAssignment()));

        assertThat(service.validateCloseRequirements(date))
                .anyMatch(error -> error.contains("active customer Pit Table assignment"));
    }

    @Test
    void activePitAssignmentForClosedSessionBlocksClose() {
        CustomerSession session = closedSession();
        stubPosition(session, BigDecimal.ZERO);
        when(assignments.findByBusinessDateAndStatusOrderByJoinedAtAsc(
                date, PitTableCustomerAssignmentStatus.ACTIVE))
                .thenReturn(List.of(new PitTableCustomerAssignment()));

        assertThat(service.validateCloseRequirements(date))
                .anyMatch(error -> error.contains("active customer Pit Table assignment"));
    }

    @Test
    void endedAssignmentAndAssignmentFromAnotherDateDoNotBlockClose() {
        LocalDate otherDate = date.minusDays(1);
        when(assignments.findByBusinessDateAndStatusOrderByJoinedAtAsc(
                otherDate, PitTableCustomerAssignmentStatus.ACTIVE))
                .thenReturn(List.of(new PitTableCustomerAssignment()));

        assertThat(service.validateCloseRequirements(date))
                .noneMatch(error -> error.contains("active customer Pit Table assignment"));
    }

    @Test
    void activeStaffAssignmentBlocksClose() {
        when(staffAssignments.findByBusinessDateAndEndedAtIsNullOrderByStartedAtAsc(date))
                .thenReturn(List.of(new PitTableStaffAssignment()));

        assertThat(service.validateCloseRequirements(date))
                .anyMatch(error -> error.contains("active staff Pit Table assignment"));
    }

    @Test
    void endedStaffAssignmentAndAssignmentFromAnotherDateDoNotBlockClose() {
        LocalDate otherDate = date.minusDays(1);
        when(staffAssignments.findByBusinessDateAndEndedAtIsNullOrderByStartedAtAsc(otherDate))
                .thenReturn(List.of(new PitTableStaffAssignment()));

        assertThat(service.validateCloseRequirements(date))
                .noneMatch(error -> error.contains("active staff Pit Table assignment"));
    }

    @Test
    void openPitTableBlocksClose() {
        PitTable table = table(date, "OPEN");
        when(tables.findByBusinessDate(date)).thenReturn(List.of(table));

        assertThat(service.validateCloseRequirements(date))
                .anyMatch(error -> error.contains("Pit Table(s) still OPEN"));
    }

    @Test
    void closedSessionPositivePositionBlocksClose() {
        CustomerSession session = closedSession();
        stubPosition(session, new BigDecimal("1000"));

        assertThat(service.validateCloseRequirements(date))
                .anyMatch(error -> error.contains("CLOSED customer session(s)")
                        && error.contains("positive"));
    }

    @Test
    void closedSessionNegativePositionBlocksClose() {
        CustomerSession session = closedSession();
        stubPosition(session, new BigDecimal("-1000"));

        assertThat(service.validateCloseRequirements(date))
                .anyMatch(error -> error.contains("CLOSED customer session(s)")
                        && error.contains("negative"));
    }

    @Test
    void closedSessionZeroPositionDoesNotBlockClose() {
        CustomerSession session = closedSession();
        stubPosition(session, BigDecimal.ZERO);

        assertThat(service.validateCloseRequirements(date))
                .noneMatch(error -> error.contains("CLOSED customer session(s)"));
    }

    @Test
    void unresolvedCustomerSessionCustodyBlocksClose() {
        CustomerSession session = closedSession();
        stubPosition(session, BigDecimal.ZERO);
        CustomerSessionCustodySummaryProjection summary = mock(CustomerSessionCustodySummaryProjection.class);
        when(summary.getCustodyTotal()).thenReturn(new BigDecimal("5000"));
        when(custody.summarizeCustomerSessions(List.of(session.getId()))).thenReturn(List.of(summary));

        assertThat(service.validateCloseRequirements(date))
                .anyMatch(error -> error.contains("customer session(s) retain unresolved physical chip custody"));
    }

    @Test
    void zeroCustomerSessionCustodyDoesNotBlockClose() {
        CustomerSession session = closedSession();
        stubPosition(session, BigDecimal.ZERO);
        CustomerSessionCustodySummaryProjection summary = mock(CustomerSessionCustodySummaryProjection.class);
        when(summary.getCustodyTotal()).thenReturn(BigDecimal.ZERO);
        when(custody.summarizeCustomerSessions(List.of(session.getId()))).thenReturn(List.of(summary));

        assertThat(service.validateCloseRequirements(date))
                .noneMatch(error -> error.contains("customer session(s) retain unresolved physical chip custody"));
    }

    @Test
    void unresolvedPitTableCustodyBlocksClose() {
        PitTable table = table(date, "CLOSED");
        when(tables.findByBusinessDate(date)).thenReturn(List.of(table));
        PitTableCustodySummaryProjection summary = mock(PitTableCustodySummaryProjection.class);
        when(summary.getCustodyTotal()).thenReturn(new BigDecimal("10000"));
        when(custody.summarizePitTables(List.of(table.getId()))).thenReturn(List.of(summary));

        assertThat(service.validateCloseRequirements(date))
                .anyMatch(error -> error.contains("Pit Table(s) retain unresolved physical chip custody"));
    }

    @Test
    void zeroPitTableCustodyDoesNotBlockClose() {
        PitTable table = table(date, "CLOSED");
        when(tables.findByBusinessDate(date)).thenReturn(List.of(table));
        PitTableCustodySummaryProjection summary = mock(PitTableCustodySummaryProjection.class);
        when(summary.getCustodyTotal()).thenReturn(BigDecimal.ZERO);
        when(custody.summarizePitTables(List.of(table.getId()))).thenReturn(List.of(summary));

        assertThat(service.validateCloseRequirements(date))
                .noneMatch(error -> error.contains("Pit Table(s) retain unresolved physical chip custody"));
    }

    @Test
    void cageInventoryAndCustodyFromAnotherBusinessDateDoNotBlockClose() {
        LocalDate otherDate = date.minusDays(1);
        CustomerSession otherSession = session(otherDate, "CLOSED");
        PitTable otherTable = table(otherDate, "CLOSED");
        when(sessions.findByBusinessDateOrderByEntryTimeAsc(otherDate)).thenReturn(List.of(otherSession));
        when(tables.findByBusinessDate(otherDate)).thenReturn(List.of(otherTable));

        assertThat(service.validateCloseRequirements(date)).isEmpty();
    }

    @Test
    void unsubmittedCashierReconciliationBlocksClose() {
        when(users.findAll()).thenReturn(List.of(activeCashier()));

        assertThat(service.validateCloseRequirements(date))
                .anyMatch(error -> error.contains("have not submitted reconciliation"));
    }

    @Test
    void reopenedCashierReconciliationBlocksClose() {
        User cashier = activeCashier();
        CashierReconciliation reconciliation = reconciliation(cashier, "REOPENED");
        when(users.findAll()).thenReturn(List.of(cashier));
        when(reconciliations.findByBusinessDateOrderBySubmittedAtDesc(date))
                .thenReturn(List.of(reconciliation));

        assertThat(service.validateCloseRequirements(date))
                .anyMatch(error -> error.contains("remain REOPENED"));
    }

    @Test
    void resolvingAllIssuesAllowsClose() {
        User cashier = activeCashier();
        when(users.findAll()).thenReturn(List.of(cashier));
        when(reconciliations.findByBusinessDateOrderBySubmittedAtDesc(date))
                .thenReturn(List.of(reconciliation(cashier, "SUBMITTED")));

        assertThat(service.validateCloseRequirements(date)).isEmpty();
    }

    @Test
    void unresolvedNonCashierCashBucketBlocksClose() {
        User admin = user(Role.SUPER_ADMIN);
        ChipBuyIn buyIn = new ChipBuyIn();
        buyIn.setCreatedBy(admin.getId());
        buyIn.setPaymentMode("CASH");
        buyIn.setAmountReceived(new BigDecimal("54000"));
        when(users.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(buyIns.findByBusinessDate(date)).thenReturn(List.of(buyIn));
        when(openingBalances.findByCashierUserIdAndBusinessDate(admin.getId(), date))
                .thenReturn(Optional.empty());
        when(reconciliations.findByCashierUserIdAndBusinessDate(admin.getId(), date))
                .thenReturn(Optional.empty());
        when(legacyResolutions.findByActorUserIdAndBusinessDate(admin.getId(), date))
                .thenReturn(Optional.empty());

        assertThat(service.validateCloseRequirements(date))
                .anyMatch(error -> error.contains("non-CASHIER CASH activity bucket"));
    }

    @Test
    void matchingLegacyNonCashierResolutionAllowsClose() {
        User admin = user(Role.SUPER_ADMIN);
        ChipBuyIn buyIn = new ChipBuyIn();
        buyIn.setCreatedBy(admin.getId());
        buyIn.setPaymentMode("CASH");
        buyIn.setAmountReceived(new BigDecimal("54000"));
        LegacyCashActorResolution resolution = new LegacyCashActorResolution();
        resolution.setActorUserId(admin.getId());
        resolution.setBusinessDate(date);
        resolution.setCashReceived(new BigDecimal("54000"));
        resolution.setCashPaid(BigDecimal.ZERO);
        resolution.setNetCashMovement(new BigDecimal("54000"));
        when(users.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(buyIns.findByBusinessDate(date)).thenReturn(List.of(buyIn));
        when(openingBalances.findByCashierUserIdAndBusinessDate(admin.getId(), date))
                .thenReturn(Optional.empty());
        when(reconciliations.findByCashierUserIdAndBusinessDate(admin.getId(), date))
                .thenReturn(Optional.empty());
        when(legacyResolutions.findByActorUserIdAndBusinessDate(admin.getId(), date))
                .thenReturn(Optional.of(resolution));

        assertThat(service.validateCloseRequirements(date)).isEmpty();
    }

    private CustomerSession openSession() {
        return session(date, "OPEN");
    }

    private CustomerSession closedSession() {
        return session(date, "CLOSED");
    }

    private CustomerSession session(LocalDate businessDate, String status) {
        CustomerSession session = new CustomerSession();
        session.setId(UUID.randomUUID());
        session.setCustomerId(UUID.randomUUID());
        session.setBusinessDate(businessDate);
        session.setStatus(status);
        when(sessions.findByBusinessDateOrderByEntryTimeAsc(businessDate))
                .thenReturn(List.of(session));
        return session;
    }

    private PitTable table(LocalDate businessDate, String status) {
        PitTable table = new PitTable();
        table.setId(UUID.randomUUID());
        table.setBusinessDate(businessDate);
        table.setStatus(status);
        return table;
    }

    private void stubPosition(CustomerSession session, BigDecimal value) {
        when(positions.getPosition(session.getId())).thenReturn(new SessionFinancialPositionResponse(
                session.getCustomerId(), session.getId(), date, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, value));
    }

    private User activeCashier() {
        User user = user(Role.CASHIER);
        user.setStatus("ACTIVE");
        return user;
    }

    private User user(Role role) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setRole(role.name());
        return user;
    }

    private CashierReconciliation reconciliation(User cashier, String status) {
        CashierReconciliation value = new CashierReconciliation();
        value.setCashierUserId(cashier.getId());
        value.setBusinessDate(date);
        value.setLifecycleStatus(status);
        return value;
    }
}
