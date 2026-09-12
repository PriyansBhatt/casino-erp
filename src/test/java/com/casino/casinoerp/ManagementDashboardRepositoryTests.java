package com.casino.casinoerp;

import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.repository.ManagementDashboardRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Uses PostgreSQL and rolls back every fixture; no dev-profile seeding or operational service writes. */
@SpringBootTest
@Transactional
class ManagementDashboardRepositoryTests {
    @Autowired EntityManager em;
    @Autowired ManagementDashboardRepository repository;
    private final LocalDate date = LocalDate.of(2198, 9, 1);

    @Test void emptyDateProducesEmptyAggregates() {
        var totals = repository.payments(date);
        assertThat(totals.buyIn()).isEqualByComparingTo("0");
        assertThat(totals.cashOut()).isEqualByComparingTo("0");
        assertThat(totals.losingReturnPaid()).isEqualByComparingTo("0");
        assertThat(repository.activeCustomers(date)).isZero();
        assertThat(repository.activeTables(date)).isZero();
        assertThat(repository.reconciliations(date).submittedCount()).isZero();
    }

    @Test void paymentsUsePersistedBusinessDateAcrossMidnightAndPaymentModes() {
        User actor = user();
        CustomerSession session = session(customer(), actor, date, "OPEN", false);
        buyIn(session, actor, date.atTime(10, 0), "100.25", "CASH");
        buyIn(session, actor, date.plusDays(1).atTime(0, 30), "200.50", "BANK");
        buyIn(session, actor, date.plusDays(1).atTime(8, 59), "300.75", "CASH");
        // Continued lifecycle posting: selected Business Date stays authoritative after its nominal window.
        buyIn(session, actor, date.plusDays(1).atTime(10, 0), "50.00", "CASH");
        cashOut(session, actor, "75.25", date.plusDays(1).atTime(2, 0));
        losingReturn(session, actor, "10.00");
        CustomerSession nextDay = session(customer(), actor, date.plusDays(1), "OPEN", false);
        buyIn(nextDay, actor, date.plusDays(1).atTime(10, 0), "999.00", "CASH");
        cashOut(nextDay, actor, "99.00", date.plusDays(1).atTime(11, 0));
        losingReturn(nextDay, actor, "50.00");
        em.flush();
        em.clear();
        var result = repository.payments(date);
        assertThat(result.buyIn()).isEqualByComparingTo("651.50");
        assertThat(result.cashOut()).isEqualByComparingTo("75.25");
        assertThat(result.losingReturnPaid()).isEqualByComparingTo("10.00");
        assertThat(repository.payments(date.plusDays(1)).buyIn()).isEqualByComparingTo("999.00");
    }

    @Test void customerCountDeduplicatesAndExcludesClosedExitedAndOtherDates() {
        User actor = user();
        Customer current = customer();
        session(current, actor, date, "OPEN", false);
        session(current, actor, date, "OPEN", false);
        session(customer(), actor, date, "CLOSED", false);
        session(customer(), actor, date, "OPEN", true);
        session(customer(), actor, date.plusDays(1), "OPEN", false);
        em.flush();
        assertThat(repository.activeCustomers(date)).isEqualTo(1);
    }

    @Test void tableCountUsesOpenPhysicalTableSessionsForSelectedDate() {
        table(date, "OPEN", false);
        table(date, "CLOSED", true);
        table(date, "OPEN", true);
        table(date.plusDays(1), "OPEN", false);
        em.flush();
        assertThat(repository.activeTables(date)).isEqualTo(1);
    }

    @Test void varianceSumsSubmittedOnlyAndPreservesNegativeValues() {
        reconciliation(date, "SUBMITTED", "10.50");
        reconciliation(date, "SUBMITTED", "-30.25");
        reconciliation(date, "REOPENED", "999.00");
        reconciliation(date.plusDays(1), "SUBMITTED", "500.00");
        em.flush();
        var result = repository.reconciliations(date);
        assertThat(result.submittedCount()).isEqualTo(2);
        assertThat(result.variance()).isEqualByComparingTo("-19.75");
    }

    private User user() {
        User value = new User();
        value.setId(UUID.randomUUID());
        value.setUsername("dash-" + value.getId());
        value.setPasswordHash("test-only");
        value.setFullName("Dashboard test cashier");
        value.setStatus("ACTIVE");
        value.setRole("CASHIER");
        value.setCreatedAt(date.atTime(9, 0));
        em.persist(value);
        return value;
    }

    private Customer customer() {
        Customer value = new Customer();
        value.setId(UUID.randomUUID());
        value.setCustomerCode("D-" + value.getId());
        value.setFullName("Dashboard test customer");
        value.setPhone("9800000000");
        value.setNationality("Nepali");
        value.setStatus(CustomerStatus.ACTIVE);
        value.setCategory(CustomerCategory.NORMAL);
        value.setKycStatus(KycStatus.PENDING);
        value.setCreatedAt(date.atTime(9, 0));
        em.persist(value);
        return value;
    }

    private CustomerSession session(Customer customer, User actor, LocalDate date, String status, boolean exited) {
        CustomerSession value = new CustomerSession();
        value.setId(UUID.randomUUID());
        value.setSessionCode("D-" + value.getId());
        value.setCustomerId(customer.getId());
        value.setSessionDate(date);
        value.setBusinessDate(date);
        value.setStatus(status);
        value.setEntryTime(date.atTime(9, 0));
        value.setExitTime(exited ? date.atTime(11, 0) : null);
        value.setOpenedBy(actor.getId());
        value.setCreatedAt(date.atTime(9, 0));
        em.persist(value);
        return value;
    }

    private void buyIn(CustomerSession session, User actor, LocalDateTime at, String amount, String paymentMode) {
        ChipBuyIn value = new ChipBuyIn();
        value.setId(UUID.randomUUID());
        value.setBuyInCode("D-" + value.getId());
        value.setCustomerId(session.getCustomerId());
        value.setCustomerSessionId(session.getId());
        value.setBusinessDate(session.getBusinessDate());
        value.setCreatedBy(actor.getId());
        value.setCreatedAt(at);
        value.setAmountReceived(new BigDecimal(amount));
        value.setTotalChipValueIssued(new BigDecimal(amount));
        value.setPaymentMode(paymentMode);
        value.setIdempotencyKey("dashboard-buyin-" + value.getId());
        em.persist(value);
    }

    private void cashOut(CustomerSession session, User actor, String amount, LocalDateTime at) {
        ChipCashOut value = new ChipCashOut();
        value.setCashOutCode("D-" + UUID.randomUUID());
        value.setCustomerId(session.getCustomerId());
        value.setCustomerSessionId(session.getId());
        value.setBusinessDate(session.getBusinessDate());
        value.setCreatedBy(actor.getId());
        value.setCreatedAt(at);
        value.setCashPaid(new BigDecimal(amount));
        value.setTotalChipValueReturned(new BigDecimal(amount));
        value.setPaymentMode("CASH");
        value.setSameCustomerVerified(true);
        value.setThirdPartyAttempt(false);
        value.setIdempotencyKey("dashboard-cashout-" + UUID.randomUUID());
        em.persist(value);
    }

    private void losingReturn(CustomerSession session, User actor, String amount) {
        LosingReturn value = new LosingReturn();
        value.setLosingReturnCode("D-" + UUID.randomUUID());
        value.setCustomerId(session.getCustomerId());
        value.setCustomerSessionId(session.getId());
        value.setBusinessDate(session.getBusinessDate());
        value.setCreatedBy(actor.getId());
        value.setCreatedAt(session.getBusinessDate().plusDays(1).atTime(3, 0));
        value.setEligibleVerifiedLoss(new BigDecimal("20000"));
        value.setReturnRate(new BigDecimal("0.01"));
        value.setAmountPaid(new BigDecimal(amount));
        value.setPaymentMode("CASH");
        value.setIdempotencyKey("dashboard-return-" + UUID.randomUUID());
        em.persist(value);
    }

    private void table(LocalDate date, String status, boolean closed) {
        PhysicalPitTable physical = new PhysicalPitTable();
        physical.setTableCode("D-" + UUID.randomUUID());
        physical.setTableName("Dashboard test table");
        physical.setGameType("BACCARAT");
        physical.setStatus("ACTIVE");
        physical.setCreatedAt(date.atTime(9, 0));
        physical.setUpdatedAt(date.atTime(9, 0));
        em.persist(physical);
        PitTable value = new PitTable();
        value.setPhysicalTableId(physical.getId());
        value.setTableCode(physical.getTableCode());
        value.setTableName(physical.getTableName());
        value.setGameType(physical.getGameType());
        value.setStatus(status);
        value.setBusinessDate(date);
        value.setOpenedAt(date.atTime(9, 0));
        value.setClosedAt(closed ? date.atTime(12, 0) : null);
        value.setOpeningFloat(BigDecimal.ZERO);
        em.persist(value);
    }

    private void reconciliation(LocalDate date, String lifecycle, String variance) {
        CashierReconciliation value = new CashierReconciliation();
        value.setCashierUserId(user().getId());
        value.setBusinessDate(date);
        value.setOpeningCash(new BigDecimal("1000"));
        value.setExpectedClosingCash(new BigDecimal("1000"));
        value.setActualClosingCash(new BigDecimal("1000").add(new BigDecimal(variance)));
        value.setVariance(new BigDecimal(variance));
        value.setStatus(new BigDecimal(variance).signum() < 0 ? "SHORT" : "OVER");
        value.setLifecycleStatus(lifecycle);
        value.setIdempotencyKey("dashboard-reconciliation-" + UUID.randomUUID());
        value.setSubmittedAt(date.plusDays(1).atTime(8, 0));
        em.persist(value);
    }
}
