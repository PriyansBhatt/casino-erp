package com.casino.casinoerp;

import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.exception.ResourceConflictException;
import com.casino.casinoerp.exception.ResourceNotFoundException;
import com.casino.casinoerp.repository.*;
import com.casino.casinoerp.security.Role;
import com.casino.casinoerp.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChipCashOutServiceTests {
    private final ChipCashOutRepository repository = mock(ChipCashOutRepository.class);
    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final CustomerSessionRepository sessionRepository = mock(CustomerSessionRepository.class);
    private final SystemLockService lockService = mock(SystemLockService.class);
    private final BusinessDateService businessDateService = mock(BusinessDateService.class);
    private final SessionFinancialPositionService positionService = mock(SessionFinancialPositionService.class);
    private final WalletTransactionService walletService = mock(WalletTransactionService.class);
    private final AuditLogService auditService = mock(AuditLogService.class);
    private final CurrentUserRoleService roleService = mock(CurrentUserRoleService.class);
    private final AuthenticatedUserService userService = mock(AuthenticatedUserService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final CashierReconciliationService reconciliationService = mock(CashierReconciliationService.class);
    private final ChipCustodyService custodyService = mock(ChipCustodyService.class);
    private final ChipCashOutService service = new ChipCashOutService(
            repository, customerRepository, sessionRepository, lockService, businessDateService,
            positionService, walletService, auditService, new RolePermissionService(), roleService,
            userService, userRepository, reconciliationService, custodyService);

    private final UUID customerId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final LocalDate businessDate = LocalDate.of(2026, 8, 8);

    @BeforeEach void setUp() {
        when(roleService.getCurrentUserRole()).thenReturn(Role.CASHIER.name());
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer(CustomerStatus.ACTIVE)));
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session("OPEN", customerId, businessDate)));
        when(businessDateService.getCurrentBusinessDate()).thenReturn(businessDate);
        when(positionService.getPosition(sessionId)).thenReturn(position("1000"));
        when(userService.getRequiredUser()).thenReturn(actor());
        when(repository.save(any())).thenAnswer(invocation -> {
            ChipCashOut value = invocation.getArgument(0); value.setId(UUID.randomUUID()); return value;
        });
        when(walletService.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(custodyService.recordCashOut(any(), any(), any(), anyMap(), any(), any()))
                .thenAnswer(invocation -> custodyMovement(invocation.getArgument(3)));
    }

    @Test void exactMaximumCreatesServerOwnedCashOutWalletAndAudit() {
        ChipCashOutResponse response = service.create(request("1000", "1000", PaymentMode.CASH, null, "key-1"));
        assertThat(response.id()).isNotNull();
        assertThat(response.cashOutCode()).startsWith("CO-20260808-");
        assertThat(response.businessDate()).isEqualTo(businessDate);
        assertThat(response.createdBy().id()).isEqualTo(actorId);
        verify(repository).save(argThat(value -> value.getCashOutCode() != null
                && actorId.equals(value.getCreatedBy()) && value.getCreatedAt() != null
                && Boolean.TRUE.equals(value.getSameCustomerVerified())
                && Boolean.FALSE.equals(value.getThirdPartyAttempt())));
        verify(walletService).save(argThat(value -> "CASH_OUT".equals(value.getTransactionType())
                && value.getAmount().compareTo(new BigDecimal("1000")) == 0
                && customerId.equals(value.getCustomerId()) && sessionId.equals(value.getCustomerSessionId())));
        verify(auditService).log(eq("CREATE_CASH_OUT"), any(), any(), eq(actorId), any());
    }

    @Test void submittedReconciliationBlocksAllPaymentModesBeforeWrites() {
        doThrow(new ResourceConflictException("Cashier reconciliation has already been submitted for this Business Date."))
                .when(reconciliationService).validatePostingAllowed(actorId, businessDate);
        assertThatThrownBy(() -> service.create(request("500", "500", PaymentMode.QR, "QR-1", "frozen")))
                .hasMessageContaining("reconciliation has already been submitted");
        verify(repository, never()).save(any());
        verifyNoInteractions(walletService);
    }

    @Test void belowMaximumIsAllowed() {
        assertThat(service.create(request("500", "500", PaymentMode.CASH, null, "key-2")).cashPaid())
                .isEqualByComparingTo("500");
    }

    @Test void aboveMaximumIsRejected() {
        assertThatThrownBy(() -> service.create(request("1001", "1001", PaymentMode.CASH, null, "key-3")))
                .hasMessage("Cash-Out exceeds the authoritative session chip position.");
        verify(repository, never()).save(any());
    }

    @Test void zeroPositionRejectsPositiveCashOut() {
        when(positionService.getPosition(sessionId)).thenReturn(position("0"));
        assertThatThrownBy(() -> service.create(request("1", "1", PaymentMode.CASH, null, "key-4")))
                .hasMessage("Cash-Out exceeds the authoritative session chip position.");
    }

    @Test void secondAttemptSeesPositionAfterFirstCashOutAndIsRejected() {
        when(positionService.getPosition(sessionId)).thenReturn(position("1000"), position("0"));

        assertThat(service.create(request("1000", "1000", PaymentMode.CASH, null, "first")).id()).isNotNull();
        assertThatThrownBy(() -> service.create(request("1", "1", PaymentMode.CASH, null, "second")))
                .hasMessage("Cash-Out exceeds the authoritative session chip position.");

        verify(sessionRepository, times(2)).findById(sessionId);
        verify(repository, times(1)).save(any());
        verify(walletService, times(1)).save(any());
    }

    @Test void idempotencyCreatedWhileWaitingForLockReturnsOriginalWithoutWrites() {
        ChipCashOut existing = existing();
        when(repository.findByIdempotencyKey("concurrent-key"))
                .thenReturn(Optional.empty(), Optional.of(existing));
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor()));

        assertThat(service.create(request("100", "100", PaymentMode.CASH, null, "concurrent-key")).id())
                .isEqualTo(existing.getId());

        verify(sessionRepository).findById(sessionId);
        verify(repository, never()).save(any());
        verify(walletService, never()).save(any());
    }

    @Test void nonPositiveAndUnequalAmountsAreRejected() {
        assertThatThrownBy(() -> service.create(request("0", "0", PaymentMode.CASH, null, "key-5")))
                .hasMessage("Cash paid must be greater than 0.");
        assertThatThrownBy(() -> service.create(request("100", "99", PaymentMode.CASH, null, "key-6")))
                .hasMessage("Cash paid must equal total chip value returned.");
    }

    @Test void nonCashModesRequireReferenceAndAcceptIt() {
        for (PaymentMode mode : new PaymentMode[]{PaymentMode.BANK, PaymentMode.QR, PaymentMode.CARD}) {
            assertThatThrownBy(() -> service.create(request("100", "100", mode, " ", "missing-" + mode)))
                    .hasMessage("Payment reference is required for non-cash payment modes.");
            assertThat(service.create(request("100", "100", mode, " REF ", "valid-" + mode)).paymentReference())
                    .isEqualTo("REF");
        }
    }

    @Test void missingOrInactiveCustomerIsRejected() {
        when(customerRepository.findById(customerId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(request("100", "100", PaymentMode.CASH, null, "key-7")))
                .isInstanceOf(ResourceNotFoundException.class);
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer(CustomerStatus.BLOCKED)));
        assertThatThrownBy(() -> service.create(request("100", "100", PaymentMode.CASH, null, "key-8")))
                .hasMessage("Customer must be ACTIVE to create a cash-out.");
    }

    @Test void invalidSessionVariantsAreRejected() {
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(request("100", "100", PaymentMode.CASH, null, "s1")))
                .isInstanceOf(ResourceNotFoundException.class);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session("CLOSED", customerId, businessDate)));
        assertThatThrownBy(() -> service.create(request("100", "100", PaymentMode.CASH, null, "s2")))
                .hasMessage("Customer session must be OPEN.");
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session("OPEN", UUID.randomUUID(), businessDate)));
        assertThatThrownBy(() -> service.create(request("100", "100", PaymentMode.CASH, null, "s3")))
                .hasMessage("Customer session does not belong to the supplied customer.");
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session("OPEN", customerId, businessDate.minusDays(1))));
        assertThatThrownBy(() -> service.create(request("100", "100", PaymentMode.CASH, null, "s4")))
                .hasMessage("Customer session does not belong to the current OPEN Business Date.");
    }

    @Test void lockAndUnauthorizedRoleAreRejected() {
        when(lockService.isSystemLocked()).thenReturn(true);
        assertThatThrownBy(() -> service.create(request("100", "100", PaymentMode.CASH, null, "key-9")))
                .hasMessage("System is locked. Cash-Out transactions are not allowed.");
        when(lockService.isSystemLocked()).thenReturn(false);
        when(roleService.getCurrentUserRole()).thenReturn(Role.DIRECTOR.name());
        assertThatThrownBy(() -> service.create(request("100", "100", PaymentMode.CASH, null, "key-10")))
                .hasMessageContaining("Only Cashier or Super Admin");
    }

    @Test void identicalReplayReturnsExistingWithoutWrites() {
        ChipCashOut existing = existing();
        when(repository.findByIdempotencyKey("key-11")).thenReturn(Optional.of(existing));
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor()));
        assertThat(service.create(request("100", "100", PaymentMode.CASH, null, "key-11")).id())
                .isEqualTo(existing.getId());
        verify(repository, never()).save(any());
        verify(walletService, never()).save(any());
    }

    @Test void changedReplayConflicts() {
        ChipCashOut existing = existing(); existing.setCashPaid(new BigDecimal("99"));
        when(repository.findByIdempotencyKey("key-12")).thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> service.create(request("100", "100", PaymentMode.CASH, null, "key-12")))
                .isInstanceOf(ResourceConflictException.class);
    }

    @Test void failuresDoNotProceedToLaterWrites() {
        doThrow(new RuntimeException("cash-out failed")).when(repository).save(any());
        assertThatThrownBy(() -> service.create(request("100", "100", PaymentMode.CASH, null, "key-13")))
                .hasMessage("cash-out failed");
        verify(walletService, never()).save(any());
        verify(auditService, never()).log(any(), any(), any(), any(), any());
    }

    @Test void noOpenBusinessDateDoesNotConsumeIdempotencyKey() {
        doThrow(new RuntimeException("Current business date is not opened."))
                .when(businessDateService).validateBusinessDateIsOpen();
        assertThatThrownBy(() -> service.create(request("100", "100", PaymentMode.CASH, null, "retryable-key")))
                .hasMessage("Current business date is not opened.");
        verify(repository, never()).save(any());
        verify(walletService, never()).save(any());
    }

    @Test void walletFailureDoesNotWriteAudit() {
        when(walletService.save(any())).thenThrow(new RuntimeException("wallet failed"));
        assertThatThrownBy(() -> service.create(request("100", "100", PaymentMode.CASH, null, "key-14")))
                .hasMessage("wallet failed");
        verify(repository).save(any());
        verify(auditService, never()).log(any(), any(), any(), any(), any());
    }

    private CreateChipCashOutRequest request(String cash, String chips, PaymentMode mode, String reference, String key) {
        return new CreateChipCashOutRequest(customerId, sessionId, new BigDecimal(cash),
                new BigDecimal(chips), Map.of(500, 1L), mode, reference, "Test", key);
    }
    private SessionFinancialPositionResponse position(String calculated) {
        return new SessionFinancialPositionResponse(customerId, sessionId, businessDate,
                new BigDecimal("1000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal(calculated));
    }
    private Customer customer(CustomerStatus status) {
        Customer value = new Customer(); value.setId(customerId); value.setStatus(status); return value;
    }
    private CustomerSession session(String status, UUID owner, LocalDate date) {
        CustomerSession value = new CustomerSession(); value.setId(sessionId); value.setStatus(status);
        value.setCustomerId(owner); value.setBusinessDate(date); return value;
    }
    private User actor() {
        User value = new User(); value.setId(actorId); value.setUsername("cashier"); return value;
    }
    private ChipCashOut existing() {
        ChipCashOut value = new ChipCashOut(); value.setId(UUID.randomUUID()); value.setCashOutCode("CO-existing");
        value.setCustomerId(customerId); value.setCustomerSessionId(sessionId); value.setCashPaid(new BigDecimal("100"));
        value.setTotalChipValueReturned(new BigDecimal("100")); value.setPaymentMode("CASH");
        value.setDenominations(Map.of(500, 1L));
        value.setBusinessDate(businessDate); value.setCreatedBy(actorId); return value;
    }
    private ChipCustodyMovement custodyMovement(Map<Integer, Long> denominations) {
        ChipCustodyMovement movement = new ChipCustodyMovement();
        movement.setDenominations(new java.util.LinkedHashMap<>(denominations));
        return movement;
    }
}
