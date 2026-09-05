package com.casino.casinoerp;

import com.casino.casinoerp.dto.CashierReconciliationRequest;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CashierReconciliationServiceTests {
    private final CashierReconciliationRepository repository = mock(CashierReconciliationRepository.class);
    private final ChipBuyInRepository buyIns = mock(ChipBuyInRepository.class);
    private final ChipCashOutRepository cashOuts = mock(ChipCashOutRepository.class);
    private final BusinessDateService businessDates = mock(BusinessDateService.class);
    private final AuthenticatedUserService authenticatedUsers = mock(AuthenticatedUserService.class);
    private final CurrentUserRoleService roles = mock(CurrentUserRoleService.class);
    private final SystemLockService systemLock = mock(SystemLockService.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final LosingReturnRepository losingReturns = mock(LosingReturnRepository.class);
    private final CashierOpeningBalanceRepository openingBalances = mock(CashierOpeningBalanceRepository.class);
    private final CashierReconciliationService service = new CashierReconciliationService(
            repository, buyIns, cashOuts, businessDates, authenticatedUsers, roles,
            new RolePermissionService(), systemLock, audit, users, losingReturns, openingBalances);
    private final UUID actorId = UUID.randomUUID();
    private final LocalDate date = LocalDate.of(2026, 8, 8);

    @BeforeEach void setUp() {
        User actor = new User(); actor.setId(actorId); actor.setUsername("cashier"); actor.setFullName("Development Cashier");
        when(authenticatedUsers.getRequiredUser()).thenReturn(actor);
        when(roles.getCurrentRole()).thenReturn(Optional.of(Role.CASHIER));
        BusinessDate open = new BusinessDate(); open.setBusinessDate(date); open.setStatus("OPEN");
        when(businessDates.getCurrentOpenBusinessDate()).thenReturn(Optional.of(open));
        when(repository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(repository.findByCashierUserIdAndBusinessDate(actorId, date)).thenReturn(Optional.empty());
        when(buyIns.findByBusinessDateAndCreatedBy(date, actorId)).thenReturn(List.of());
        when(cashOuts.findByBusinessDateAndCreatedBy(date, actorId)).thenReturn(List.of());
        when(losingReturns.findByBusinessDateAndCreatedBy(date, actorId)).thenReturn(List.of());
        when(repository.save(any())).thenAnswer(invocation -> { CashierReconciliation value = invocation.getArgument(0); value.setId(UUID.randomUUID()); return value; });
    }

    @Test void calculatesPhysicalCashByTenderAndDenominations() {
        ChipBuyIn cashBuyIn = buyIn("CASH", "1000");
        ChipBuyIn bankBuyIn = buyIn("BANK", "9000");
        ChipCashOut cashOut = cashOut("CASH", "500");
        ChipCashOut qrCashOut = cashOut("QR", "7000");
        when(buyIns.findByBusinessDateAndCreatedBy(date, actorId)).thenReturn(List.of(cashBuyIn, bankBuyIn));
        when(cashOuts.findByBusinessDateAndCreatedBy(date, actorId)).thenReturn(List.of(cashOut, qrCashOut));

        var result = service.preview(request("250", Map.of(500, 1, 100, 2, 50, 1), "key"));

        assertThat(result.physicalCashReceived()).isEqualByComparingTo("1000");
        assertThat(result.physicalCashPaid()).isEqualByComparingTo("500");
        assertThat(result.expectedClosingCash()).isEqualByComparingTo("750");
        assertThat(result.actualClosingCash()).isEqualByComparingTo("750");
        assertThat(result.variance()).isZero();
        assertThat(result.status()).isEqualTo("BALANCED");
        assertThat(result.buyInTenders().get("BANK").amount()).isEqualByComparingTo("9000");
        assertThat(result.cashOutTenders().get("QR").amount()).isEqualByComparingTo("7000");
        verify(buyIns).findByBusinessDateAndCreatedBy(date, actorId);
        verify(cashOuts).findByBusinessDateAndCreatedBy(date, actorId);
    }

    @Test void reportsOverAndShortFromBackendCalculation() {
        assertThat(service.preview(request("100", Map.of(100, 2), "over")).status()).isEqualTo("OVER");
        assertThat(service.preview(request("200", Map.of(100, 1), "short")).status()).isEqualTo("SHORT");
    }

    @Test void rejectsUnsupportedNegativeAndOverflowingAmounts() {
        assertThatThrownBy(() -> service.preview(request("0", Map.of(25, 1), "unsupported"))).hasMessageContaining("Unsupported");
        assertThatThrownBy(() -> service.preview(request("0", Map.of(100, -1), "negative"))).hasMessageContaining("non-negative");
        assertThatThrownBy(() -> service.preview(request("100000000000000000", Map.of(), "large"))).hasMessageContaining("supported limits");
    }

    @Test void systemLockBlocksSubmitButNotView() {
        when(systemLock.isSystemLocked()).thenReturn(true);
        assertThatThrownBy(() -> service.submit(request("0", Map.of(), "locked"))).hasMessageContaining("locked");
        assertThatNoException().isThrownBy(service::getCurrent);
        verify(repository, never()).save(any());
    }

    @Test void submissionPersistsAuditAndIdempotentReplayDoesNotDuplicate() {
        var request = request("100", Map.of(100, 1), "same-key");
        var first = service.submit(request);
        var captor = org.mockito.ArgumentCaptor.forClass(CashierReconciliation.class);
        verify(repository).save(captor.capture());
        CashierReconciliation saved = captor.getValue();
        when(repository.findByIdempotencyKey("same-key")).thenReturn(Optional.of(saved));

        var replay = service.submit(request);

        assertThat(replay.id()).isEqualTo(first.id());
        verify(repository, times(1)).save(any());
        verify(audit, times(1)).log(eq("RECONCILIATION_SUBMITTED"), anyString(), any(), eq(actorId), anyString());
    }

    @Test void currentViewRestoresPersistedReconciliation() {
        CashierReconciliation existing = new CashierReconciliation(); existing.setId(UUID.randomUUID());
        existing.setBusinessDate(date); existing.setCashierUserId(actorId); existing.setOpeningCash(new BigDecimal("100"));
        existing.setExpectedClosingCash(new BigDecimal("100")); existing.setActualClosingCash(new BigDecimal("110"));
        existing.setVariance(new BigDecimal("10")); existing.setStatus("OVER"); existing.setDenominations(Map.of(100, 1, 10, 1));
        existing.setLifecycleStatus("SUBMITTED");
        when(repository.findByCashierUserIdAndBusinessDate(actorId, date)).thenReturn(Optional.of(existing));

        var result = service.getCurrent();

        assertThat(result.id()).isEqualTo(existing.getId());
        assertThat(result.status()).isEqualTo("OVER");
        assertThat(result.denominations()).containsEntry(100, 1).containsEntry(10, 1);
        assertThat(result.actualClosingCash()).isEqualByComparingTo("110");
    }

    @Test void idempotencyKeyCannotBeReusedForDifferentValues() {
        CashierReconciliation prior = new CashierReconciliation(); prior.setCashierUserId(actorId); prior.setBusinessDate(date);
        prior.setOpeningCash(BigDecimal.ZERO); prior.setActualClosingCash(BigDecimal.ZERO); prior.setDenominations(Map.of());
        when(repository.findByIdempotencyKey("used")).thenReturn(Optional.of(prior));
        assertThatThrownBy(() -> service.submit(request("10", Map.of(5, 1), "used"))).hasMessageContaining("different reconciliation");
    }

    @Test void historicalIdempotentReplayDoesNotRequireNewOpeningBalanceRecord() {
        CashierReconciliation prior = persisted("SUBMITTED");
        prior.setIdempotencyKey("historical-key");
        when(repository.findByIdempotencyKey("historical-key")).thenReturn(Optional.of(prior));
        when(openingBalances.findByCashierUserIdAndBusinessDate(actorId, date)).thenReturn(Optional.empty());

        var result = service.submit(new CashierReconciliationRequest(
                new BigDecimal("999"), Map.of(100, 1), null, "historical-key"));

        assertThat(result.id()).isEqualTo(prior.getId());
        assertThat(result.openingCash()).isEqualByComparingTo("100");
        verify(repository, never()).save(any());
    }

    @Test void clientOpeningCashCannotOverrideAuthoritativeBalance() {
        CashierOpeningBalance balance = new CashierOpeningBalance();
        balance.setOpeningCashAmount(new BigDecimal("250"));
        when(openingBalances.findByCashierUserIdAndBusinessDate(actorId, date)).thenReturn(Optional.of(balance));

        var result = service.preview(new CashierReconciliationRequest(
                new BigDecimal("999999"), Map.of(500, 1), null, "ignored-client-opening"));

        assertThat(result.openingCash()).isEqualByComparingTo("250");
        assertThat(result.expectedClosingCash()).isEqualByComparingTo("250");
    }

    @Test void missingOpeningBalancePreventsPreviewAndSubmit() {
        when(openingBalances.findByCashierUserIdAndBusinessDate(actorId, date)).thenReturn(Optional.empty());
        CashierReconciliationRequest value = new CashierReconciliationRequest(
                new BigDecimal("100"), Map.of(), null, "missing-opening");
        assertThatThrownBy(() -> service.preview(value)).hasMessageContaining("Opening Cash must be established");
        assertThatThrownBy(() -> service.submit(value)).hasMessageContaining("Opening Cash must be established");
    }

    @Test void currentViewLoadsAuthoritativeOpeningCashBeforeSubmission() {
        CashierOpeningBalance balance = new CashierOpeningBalance();
        balance.setOpeningCashAmount(new BigDecimal("400"));
        when(openingBalances.findByCashierUserIdAndBusinessDate(actorId, date)).thenReturn(Optional.of(balance));

        var result = service.getCurrent();

        assertThat(result.openingCash()).isEqualByComparingTo("400");
        assertThat(result.expectedClosingCash()).isEqualByComparingTo("400");
        assertThat(result.actualClosingCash()).isNull();
        assertThat(result.variance()).isNull();
    }

    @Test void unauthorizedRoleCannotViewOrSubmit() {
        when(roles.getCurrentRole()).thenReturn(Optional.of(Role.RECEPTIONIST));
        assertThatThrownBy(service::getCurrent).hasMessageContaining("restricted");
        assertThatThrownBy(() -> service.submit(request("0", Map.of(), "denied"))).hasMessageContaining("Only Cashier or Super Admin");
    }

    @Test void submittedRecordFreezesOnlyMatchingCashierAndBusinessDate() {
        CashierReconciliation submitted = new CashierReconciliation(); submitted.setLifecycleStatus("SUBMITTED");
        when(repository.findByCashierUserIdAndBusinessDate(actorId, date)).thenReturn(Optional.of(submitted));
        assertThatThrownBy(() -> service.validatePostingAllowed(actorId, date)).hasMessageContaining("already been submitted");
        UUID other = UUID.randomUUID();
        assertThatNoException().isThrownBy(() -> service.validatePostingAllowed(other, date));
        assertThatNoException().isThrownBy(() -> service.validatePostingAllowed(actorId, date.plusDays(1)));
    }

    @Test void directorCanReopenWithReasonAndPostingBecomesAvailable() {
        when(roles.getCurrentRole()).thenReturn(Optional.of(Role.DIRECTOR));
        User director = new User(); director.setId(UUID.randomUUID()); director.setUsername("director");
        when(authenticatedUsers.getRequiredUser()).thenReturn(director);
        CashierReconciliation submitted = persisted("SUBMITTED");
        when(repository.findById(submitted.getId())).thenReturn(Optional.of(submitted));
        doAnswer(invocation -> invocation.getArgument(0)).when(repository).save(any());

        var result = service.reopen(submitted.getId(), "Correction required");

        assertThat(result.lifecycleStatus()).isEqualTo("REOPENED");
        assertThat(submitted.getReopenReason()).isEqualTo("Correction required");
        assertThatNoException().isThrownBy(() -> service.validatePostingAllowed(actorId, date));
        verify(audit).log(eq("RECONCILIATION_REOPENED"), anyString(), eq(submitted.getId()), eq(director.getId()), contains("Correction required"));
    }

    @Test void cashierCannotReopenAndReasonIsRequired() {
        CashierReconciliation submitted = persisted("SUBMITTED");
        when(repository.findById(submitted.getId())).thenReturn(Optional.of(submitted));
        assertThatThrownBy(() -> service.reopen(submitted.getId(), "reason")).hasMessageContaining("Director or Super Admin");
        when(roles.getCurrentRole()).thenReturn(Optional.of(Role.SUPER_ADMIN));
        assertThatThrownBy(() -> service.reopen(submitted.getId(), " ")).hasMessageContaining("reason is required");
    }

    @Test void reopenedRecordCanBeResubmittedAndFinalizedAgain() {
        CashierReconciliation reopened = persisted("REOPENED");
        UUID existingId = reopened.getId();
        reopened.getDenominations().clear();
        reopened.getDenominations().putAll(Map.of(100, 1));
        reopened.setIdempotencyKey("old-key");
        when(repository.findByCashierUserIdAndBusinessDate(actorId, date)).thenReturn(Optional.of(reopened));
        doReturn(reopened).when(repository).save(same(reopened));
        var result = service.submit(request("100", Map.of(50, 2), "new-key"));

        assertThat(result.id()).isEqualTo(existingId);
        assertThat(result.lifecycleStatus()).isEqualTo("SUBMITTED");
        assertThat(result.actualClosingCash()).isEqualByComparingTo("100");
        assertThat(result.expectedClosingCash()).isEqualByComparingTo("100");
        assertThat(result.variance()).isZero();
        assertThat(reopened.getDenominations()).containsExactlyEntriesOf(Map.of(50, 2));
        assertThat(reopened.getDenominations()).isInstanceOf(LinkedHashMap.class);
        assertThat(reopened.getIdempotencyKey()).isEqualTo("new-key");
        verify(repository).save(same(reopened));
        verify(audit).log(eq("RECONCILIATION_SUBMITTED"), eq("CASHIER_RECONCILIATION"),
                eq(existingId), eq(actorId), contains("result=BALANCED"));
    }

    private CashierReconciliationRequest request(String opening, Map<Integer, Integer> denominations, String key) {
        CashierOpeningBalance balance = new CashierOpeningBalance();
        balance.setOpeningCashAmount(new BigDecimal(opening));
        when(openingBalances.findByCashierUserIdAndBusinessDate(actorId, date)).thenReturn(Optional.of(balance));
        return new CashierReconciliationRequest(new BigDecimal(opening), denominations, null, key);
    }
    private ChipBuyIn buyIn(String mode, String amount) { ChipBuyIn value = new ChipBuyIn(); value.setPaymentMode(mode); value.setAmountReceived(new BigDecimal(amount)); return value; }
    private ChipCashOut cashOut(String mode, String amount) { ChipCashOut value = new ChipCashOut(); value.setPaymentMode(mode); value.setCashPaid(new BigDecimal(amount)); return value; }
    private CashierReconciliation persisted(String lifecycle) {
        CashierReconciliation value = new CashierReconciliation(); value.setId(UUID.randomUUID()); value.setCashierUserId(actorId);
        value.setBusinessDate(date); value.setOpeningCash(new BigDecimal("100")); value.setExpectedClosingCash(new BigDecimal("100"));
        value.setActualClosingCash(new BigDecimal("100")); value.setVariance(BigDecimal.ZERO); value.setStatus("BALANCED");
        value.setLifecycleStatus(lifecycle); value.setDenominations(new LinkedHashMap<>(Map.of(100, 1))); value.setIdempotencyKey("key");
        return value;
    }
}
