package com.casino.casinoerp;

import com.casino.casinoerp.dto.CreateCashierOpeningBalanceRequest;
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

class CashierOpeningBalanceServiceTests {
    private final CashierOpeningBalanceRepository repository = mock(CashierOpeningBalanceRepository.class);
    private final ChipBuyInRepository buyIns = mock(ChipBuyInRepository.class);
    private final ChipCashOutRepository cashOuts = mock(ChipCashOutRepository.class);
    private final LosingReturnRepository losingReturns = mock(LosingReturnRepository.class);
    private final BusinessDateService businessDates = mock(BusinessDateService.class);
    private final AuthenticatedUserService authenticatedUsers = mock(AuthenticatedUserService.class);
    private final CurrentUserRoleService roles = mock(CurrentUserRoleService.class);
    private final SystemLockService systemLock = mock(SystemLockService.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final CashierReconciliationReadRepository reads = mock(CashierReconciliationReadRepository.class);
    private final CashierOpeningBalanceService service = new CashierOpeningBalanceService(
            repository, reads, businessDates,
            authenticatedUsers, roles, systemLock, audit);
    private final UUID actorId = UUID.randomUUID();
    private final LocalDate date = LocalDate.of(2026, 8, 8);

    @BeforeEach
    void setUp() {
        User actor = new User();
        actor.setId(actorId); actor.setUsername("cashier"); actor.setFullName("Development Cashier");
        when(authenticatedUsers.getRequiredUser()).thenReturn(actor);
        when(roles.getCurrentRole()).thenReturn(Optional.of(Role.CASHIER));
        BusinessDate open = new BusinessDate(); open.setBusinessDate(date); open.setStatus("OPEN");
        when(businessDates.getCurrentOpenBusinessDate()).thenReturn(Optional.of(open));
        when(repository.findByCashierUserIdAndBusinessDate(actorId, date)).thenReturn(Optional.empty());
        when(buyIns.findByBusinessDateAndCreatedBy(date, actorId)).thenReturn(List.of());
        when(cashOuts.findByBusinessDateAndCreatedBy(date, actorId)).thenReturn(List.of());
        when(losingReturns.findByBusinessDateAndCreatedBy(date, actorId)).thenReturn(List.of());
        when(reads.hasActivity(actorId, date)).thenAnswer(i -> !buyIns.findByBusinessDateAndCreatedBy(date, actorId).isEmpty()
                || !cashOuts.findByBusinessDateAndCreatedBy(date, actorId).isEmpty()
                || !losingReturns.findByBusinessDateAndCreatedBy(date, actorId).isEmpty());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> {
            CashierOpeningBalance value = invocation.getArgument(0);
            value.setId(UUID.randomUUID());
            return value;
        });
    }

    @Test
    void createsOwnOpeningBalanceForCurrentOpenBusinessDate() {
        var result = service.create(new CreateCashierOpeningBalanceRequest(new BigDecimal("500000"), date));

        assertThat(result.cashierUserId()).isEqualTo(actorId);
        assertThat(result.businessDate()).isEqualTo(date);
        assertThat(result.openingCashAmount()).isEqualByComparingTo("500000");
        verify(audit).log(eq("CASHIER_OPENING_BALANCE_CREATED"), eq("CASHIER_OPENING_BALANCE"),
                any(), eq(actorId), contains("500000"));
    }

    @Test
    void rejectsNegativeDuplicateAndSystemLockedCreation() {
        assertThatThrownBy(() -> service.create(new CreateCashierOpeningBalanceRequest(new BigDecimal("-1"), date)))
                .hasMessageContaining("zero or greater");

        CashierOpeningBalance existing = new CashierOpeningBalance();
        when(repository.findByCashierUserIdAndBusinessDate(actorId, date)).thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> service.create(new CreateCashierOpeningBalanceRequest(BigDecimal.ZERO, date)))
                .hasMessageContaining("already been established");

        when(repository.findByCashierUserIdAndBusinessDate(actorId, date)).thenReturn(Optional.empty());
        when(systemLock.isSystemLocked()).thenReturn(true);
        assertThatThrownBy(() -> service.create(new CreateCashierOpeningBalanceRequest(BigDecimal.ZERO, date)))
                .hasMessageContaining("System is locked");
    }

    @Test
    void rejectsCreationAfterAnySupportedFinancialPosting() {
        when(buyIns.findByBusinessDateAndCreatedBy(date, actorId)).thenReturn(List.of(new ChipBuyIn()));
        assertThatThrownBy(() -> service.create(new CreateCashierOpeningBalanceRequest(BigDecimal.ZERO, date)))
                .hasMessageContaining("financial activity");

        when(buyIns.findByBusinessDateAndCreatedBy(date, actorId)).thenReturn(List.of());
        when(cashOuts.findByBusinessDateAndCreatedBy(date, actorId)).thenReturn(List.of(new ChipCashOut()));
        assertThatThrownBy(() -> service.create(new CreateCashierOpeningBalanceRequest(BigDecimal.ZERO, date)))
                .hasMessageContaining("financial activity");

        when(cashOuts.findByBusinessDateAndCreatedBy(date, actorId)).thenReturn(List.of());
        when(losingReturns.findByBusinessDateAndCreatedBy(date, actorId)).thenReturn(List.of(new LosingReturn()));
        assertThatThrownBy(() -> service.create(new CreateCashierOpeningBalanceRequest(BigDecimal.ZERO, date)))
                .hasMessageContaining("financial activity");
    }

    @Test
    void directorCannotCreateOperationalOpeningBalance() {
        when(roles.getCurrentRole()).thenReturn(Optional.of(Role.DIRECTOR));
        assertThatThrownBy(() -> service.create(new CreateCashierOpeningBalanceRequest(BigDecimal.ZERO, date)))
                .hasMessageContaining("Only Cashier or Super Admin");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void creationIsAlwaysScopedToAuthenticatedUser() {
        service.create(new CreateCashierOpeningBalanceRequest(new BigDecimal("100"), date));
        var captor = org.mockito.ArgumentCaptor.forClass(CashierOpeningBalance.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getCashierUserId()).isEqualTo(actorId);
        assertThat(captor.getValue().getCreatedBy()).isEqualTo(actorId);
    }
    @Test void rejectsExcessPrecisionAndWrongExpectedDate() {
        assertThatThrownBy(() -> service.create(new CreateCashierOpeningBalanceRequest(new BigDecimal("1.001"), date)))
                .hasMessageContaining("two decimal places");
        assertThatThrownBy(() -> service.create(new CreateCashierOpeningBalanceRequest(BigDecimal.ZERO, date.minusDays(1))))
                .hasMessageContaining("Business Date changed");
        verify(repository, never()).saveAndFlush(any());
    }
    @Test void zeroAndTwoDecimalOpeningAreAccepted() {
        assertThat(service.create(new CreateCashierOpeningBalanceRequest(new BigDecimal("0.00"), date)).openingCashAmount()).isZero();
    }
}
