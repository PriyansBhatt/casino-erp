package com.casino.casinoerp;

import com.casino.casinoerp.dto.LegacyCashActorResolutionRequest;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.exception.ResourceConflictException;
import com.casino.casinoerp.repository.*;
import com.casino.casinoerp.security.Role;
import com.casino.casinoerp.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LegacyCashActorResolutionServiceTests {
    private final LegacyCashActorResolutionRepository resolutions = mock(LegacyCashActorResolutionRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final CashierOpeningBalanceRepository openingBalances = mock(CashierOpeningBalanceRepository.class);
    private final CashierReconciliationRepository reconciliations = mock(CashierReconciliationRepository.class);
    private final ChipBuyInRepository buyIns = mock(ChipBuyInRepository.class);
    private final ChipCashOutRepository cashOuts = mock(ChipCashOutRepository.class);
    private final LosingReturnRepository losingReturns = mock(LosingReturnRepository.class);
    private final BusinessDateService businessDates = mock(BusinessDateService.class);
    private final SystemLockService systemLock = mock(SystemLockService.class);
    private final AuthenticatedUserService authenticatedUser = mock(AuthenticatedUserService.class);
    private final CurrentUserRoleService currentRole = mock(CurrentUserRoleService.class);
    private final RolePermissionService permissions = new RolePermissionService();
    private final AuditLogService audit = mock(AuditLogService.class);
    private final LegacyCashActorResolutionService service = new LegacyCashActorResolutionService(
            resolutions, users, openingBalances, reconciliations, buyIns, cashOuts,
            losingReturns, businessDates, systemLock, authenticatedUser, currentRole,
            permissions, audit);
    private final LocalDate date = LocalDate.of(2026, 8, 8);
    private final UUID targetId = UUID.randomUUID();
    private final UUID resolverId = UUID.randomUUID();
    private final LegacyCashActorResolutionRequest request = new LegacyCashActorResolutionRequest(
            "Legacy actor activity has no authoritative opening cash.", "legacy-admin-001");

    @BeforeEach
    void validState() {
        BusinessDate businessDate = new BusinessDate();
        businessDate.setBusinessDate(date);
        businessDate.setStatus("OPEN");
        when(currentRole.getCurrentRole()).thenReturn(Optional.of(Role.SUPER_ADMIN));
        when(businessDates.getCurrentOpenBusinessDate()).thenReturn(Optional.of(businessDate));
        when(systemLock.isSystemLocked()).thenReturn(false);
        when(users.findById(targetId)).thenReturn(Optional.of(user(targetId, "admin", Role.SUPER_ADMIN)));
        when(authenticatedUser.getRequiredUser()).thenReturn(user(resolverId, "superadmin", Role.SUPER_ADMIN));
        when(resolutions.findByIdempotencyKey(request.idempotencyKey())).thenReturn(Optional.empty());
        when(resolutions.findByActorUserIdAndBusinessDate(targetId, date)).thenReturn(Optional.empty());
        when(openingBalances.findByCashierUserIdAndBusinessDate(targetId, date)).thenReturn(Optional.empty());
        when(reconciliations.findByCashierUserIdAndBusinessDate(targetId, date)).thenReturn(Optional.empty());
        when(buyIns.findByBusinessDateAndCreatedBy(date, targetId))
                .thenReturn(List.of(buyIn("54000", "CASH")));
        when(cashOuts.findByBusinessDateAndCreatedBy(date, targetId))
                .thenReturn(List.of(cashOut("35000", "CASH")));
        when(losingReturns.findByBusinessDateAndCreatedBy(date, targetId))
                .thenReturn(List.of(losingReturn("2200", "CASH")));
        when(resolutions.saveAndFlush(any())).thenAnswer(invocation -> {
            LegacyCashActorResolution value = invocation.getArgument(0);
            value.setId(UUID.randomUUID());
            return value;
        });
    }

    @Test
    void superAdminResolutionDerivesImmutableCashTotals() {
        var response = service.resolve(targetId, request);

        assertThat(response.cashReceived()).isEqualByComparingTo("54000");
        assertThat(response.cashPaid()).isEqualByComparingTo("37200");
        assertThat(response.netCashMovement()).isEqualByComparingTo("16800");
        assertThat(response.status()).isEqualTo("LEGACY_RESOLVED");
        assertThat(response.openingCashVerification()).isEqualTo("LEGACY_UNVERIFIED");
        assertThat(response.expectedClosing()).isNull();
        assertThat(response.variance()).isNull();
        verify(openingBalances, never()).save(any());
        verify(reconciliations, never()).save(any());
        verify(buyIns, never()).save(any());
        verify(cashOuts, never()).save(any());
        verify(losingReturns, never()).save(any());
        verify(audit).log(eq("LEGACY_CASH_ACTOR_BUCKET_RESOLVED"),
                eq("CASHIER_RECONCILIATION"), any(), eq(resolverId), contains("netCashMovement=16800"));
    }

    @Test
    void directorAndCashierCannotResolve() {
        for (Role role : List.of(Role.DIRECTOR, Role.CASHIER)) {
            reset(resolutions);
            when(currentRole.getCurrentRole()).thenReturn(Optional.of(role));
            assertThatThrownBy(() -> service.resolve(targetId, request))
                    .hasMessageContaining("Only Super Admin");
            verifyNoInteractions(resolutions);
        }
    }

    @Test
    void cashierTargetIsRejected() {
        when(users.findById(targetId)).thenReturn(Optional.of(user(targetId, "cashier", Role.CASHIER)));
        assertThatThrownBy(() -> service.resolve(targetId, request))
                .isInstanceOf(ResourceConflictException.class).hasMessageContaining("normal Opening Cash");
    }

    @Test
    void existingOpeningBalanceOrReconciliationIsRejected() {
        when(openingBalances.findByCashierUserIdAndBusinessDate(targetId, date))
                .thenReturn(Optional.of(new CashierOpeningBalance()));
        assertThatThrownBy(() -> service.resolve(targetId, request)).hasMessageContaining("Opening Cash");
        when(openingBalances.findByCashierUserIdAndBusinessDate(targetId, date)).thenReturn(Optional.empty());
        when(reconciliations.findByCashierUserIdAndBusinessDate(targetId, date))
                .thenReturn(Optional.of(new CashierReconciliation()));
        assertThatThrownBy(() -> service.resolve(targetId, request)).hasMessageContaining("normal cashier reconciliation");
    }

    @Test
    void noCashActivityAndSystemLockAreRejected() {
        when(buyIns.findByBusinessDateAndCreatedBy(date, targetId)).thenReturn(List.of());
        when(cashOuts.findByBusinessDateAndCreatedBy(date, targetId)).thenReturn(List.of());
        when(losingReturns.findByBusinessDateAndCreatedBy(date, targetId)).thenReturn(List.of());
        assertThatThrownBy(() -> service.resolve(targetId, request)).hasMessageContaining("no persisted CASH activity");
        when(systemLock.isSystemLocked()).thenReturn(true);
        assertThatThrownBy(() -> service.resolve(targetId, request)).hasMessageContaining("System is locked");
    }

    @Test
    void missingOpenBusinessDateIsRejected() {
        when(businessDates.getCurrentOpenBusinessDate()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.resolve(targetId, request))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("not opened");
    }

    @Test
    void exactIdempotentReplayReturnsOriginalWithoutWriting() {
        LegacyCashActorResolution existing = resolution();
        when(resolutions.findByIdempotencyKey(request.idempotencyKey())).thenReturn(Optional.of(existing));
        var response = service.resolve(targetId, request);
        assertThat(response.id()).isEqualTo(existing.getId());
        verify(resolutions, never()).saveAndFlush(any());
    }

    @Test
    void reusedIdempotencyKeyWithDifferentTargetIsRejected() {
        LegacyCashActorResolution existing = resolution();
        existing.setActorUserId(UUID.randomUUID());
        when(resolutions.findByIdempotencyKey(request.idempotencyKey())).thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> service.resolve(targetId, request))
                .isInstanceOf(ResourceConflictException.class).hasMessageContaining("different legacy resolution");
    }

    private LegacyCashActorResolution resolution() {
        LegacyCashActorResolution value = new LegacyCashActorResolution();
        value.setId(UUID.randomUUID()); value.setActorUserId(targetId); value.setBusinessDate(date);
        value.setCashReceived(new BigDecimal("54000")); value.setCashPaid(new BigDecimal("37200"));
        value.setNetCashMovement(new BigDecimal("16800")); value.setOpeningCashVerification("LEGACY_UNVERIFIED");
        value.setReason(request.reason()); value.setResolvedBy(resolverId); value.setResolvedAt(java.time.LocalDateTime.now());
        value.setIdempotencyKey(request.idempotencyKey()); return value;
    }
    private User user(UUID id, String username, Role role) { User value = new User(); value.setId(id); value.setUsername(username); value.setRole(role.name()); return value; }
    private ChipBuyIn buyIn(String amount, String mode) { ChipBuyIn value = new ChipBuyIn(); value.setAmountReceived(new BigDecimal(amount)); value.setPaymentMode(mode); return value; }
    private ChipCashOut cashOut(String amount, String mode) { ChipCashOut value = new ChipCashOut(); value.setCashPaid(new BigDecimal(amount)); value.setPaymentMode(mode); return value; }
    private LosingReturn losingReturn(String amount, String mode) { LosingReturn value = new LosingReturn(); value.setAmountPaid(new BigDecimal(amount)); value.setPaymentMode(mode); return value; }
}
