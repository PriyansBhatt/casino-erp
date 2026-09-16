package com.casino.casinoerp;

import com.casino.casinoerp.dto.SessionFinancialPositionResponse;
import com.casino.casinoerp.entity.CustomerSession;
import com.casino.casinoerp.entity.PitTable;
import com.casino.casinoerp.entity.PitTableCustomerAssignment;
import com.casino.casinoerp.entity.PitTableCustomerAssignmentStatus;
import com.casino.casinoerp.entity.PitTableStaffAssignment;
import com.casino.casinoerp.repository.CustomerSessionRepository;
import com.casino.casinoerp.repository.PitTableCustomerAssignmentRepository;
import com.casino.casinoerp.repository.PitTableRepository;
import com.casino.casinoerp.repository.PitTableStaffAssignmentRepository;
import com.casino.casinoerp.repository.ChipCustodyInventoryRepository;
import com.casino.casinoerp.repository.CustomerSessionCustodySummaryProjection;
import com.casino.casinoerp.repository.PitTableCustodySummaryProjection;
import com.casino.casinoerp.service.BusinessDateValidationService;
import com.casino.casinoerp.service.SessionFinancialPositionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
    private final com.casino.casinoerp.repository.BusinessDateCloseReadRepository closeReads =
            mock(com.casino.casinoerp.repository.BusinessDateCloseReadRepository.class);
    private final BusinessDateValidationService service = new BusinessDateValidationService(
            sessions, assignments, positions, tables, staffAssignments, custody, closeReads);
    private final LocalDate date = LocalDate.of(2026, 8, 8);

    @BeforeEach
    void settledBusinessDate() {
        when(sessions.findByBusinessDateOrderByEntryTimeAsc(date)).thenReturn(List.of());
        when(assignments.findByBusinessDateAndStatusOrderByJoinedAtAsc(
                date, PitTableCustomerAssignmentStatus.ACTIVE)).thenReturn(List.of());
        when(staffAssignments.findByBusinessDateAndEndedAtIsNullOrderByStartedAtAsc(date))
                .thenReturn(List.of());
        when(tables.findByBusinessDate(date)).thenReturn(List.of());
        when(closeReads.actors(date)).thenReturn(List.of());
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
    void missingReconciliationForCashActorBlocksRegardlessOfLoginEligibility() {
        actor(false, null, false);
        assertThat(service.validateCloseRequirements(date))
                .anyMatch(error -> error.contains("have not submitted reconciliation"));
    }

    @Test
    void openingBalanceAloneStillRequiresSubmission() {
        actor(true, null, false);
        assertThat(service.validateCloseRequirements(date)).isNotEmpty();
    }

    @Test
    void accountExistenceWithoutFinancialResponsibilityDoesNotBlock() {
        assertThat(service.validateCloseRequirements(date)).isEmpty();
        org.mockito.Mockito.verify(closeReads).actors(date);
    }

    @Test
    void reopenedReconciliationCannotBeBypassedByLegacyResolution() {
        actor(true, "REOPENED", true);
        assertThat(service.validateCloseRequirements(date))
                .anyMatch(error -> error.contains("remain REOPENED"));
    }

    @Test
    void completeNormalChainAllowsClose() {
        actor(true, "SUBMITTED", false);
        assertThat(service.validateCloseRequirements(date)).isEmpty();
    }

    @Test
    void submittedWithoutOpeningIsNotACompleteChain() {
        actor(false, "SUBMITTED", false);
        assertThat(service.validateCloseRequirements(date))
                .anyMatch(error -> error.contains("Opening Cash"));
    }

    @Test
    void matchingLegacyResolutionStillAllowsClose() {
        actor(false, null, true);
        assertThat(service.validateCloseRequirements(date)).isEmpty();
    }

    @Test
    void staleLegacyResolutionBlocksClose() {
        actor(false, null, false);
        assertThat(service.validateCloseRequirements(date)).isNotEmpty();
    }

    private void actor(boolean opening, String status, boolean legacy) {
        when(closeReads.actors(date)).thenReturn(List.of(
                new com.casino.casinoerp.repository.BusinessDateCloseReadRepository.Actor(
                        UUID.randomUUID(), opening, status, legacy)));
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

}
