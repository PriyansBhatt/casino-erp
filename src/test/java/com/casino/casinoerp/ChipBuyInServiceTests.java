package com.casino.casinoerp;

import com.casino.casinoerp.dto.ChipBuyInResponse;
import com.casino.casinoerp.dto.CreateChipBuyInRequest;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.exception.ResourceNotFoundException;
import com.casino.casinoerp.exception.ResourceConflictException;
import com.casino.casinoerp.repository.*;
import com.casino.casinoerp.security.Role;
import com.casino.casinoerp.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChipBuyInServiceTests {
    private final ChipBuyInRepository buyInRepository = mock(ChipBuyInRepository.class);
    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final CustomerSessionRepository sessionRepository = mock(CustomerSessionRepository.class);
    private final SystemLockService systemLockService = mock(SystemLockService.class);
    private final WalletTransactionService walletTransactionService = mock(WalletTransactionService.class);
    private final BusinessDateService businessDateService = mock(BusinessDateService.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final CurrentUserRoleService currentUserRoleService = mock(CurrentUserRoleService.class);
    private final AuthenticatedUserService authenticatedUserService = mock(AuthenticatedUserService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final CashierReconciliationService reconciliationService = mock(CashierReconciliationService.class);
    private final ChipCustodyService chipCustodyService = mock(ChipCustodyService.class);
    private final ChipBuyInService service = new ChipBuyInService(
            buyInRepository, customerRepository, sessionRepository, systemLockService,
            walletTransactionService, businessDateService, auditLogService,
            new RolePermissionService(), currentUserRoleService, authenticatedUserService,
            userRepository, reconciliationService, chipCustodyService);

    private final UUID customerId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final LocalDate businessDate = LocalDate.of(2026, 8, 8);

    @BeforeEach
    void setUp() {
        when(currentUserRoleService.getCurrentUserRole()).thenReturn(Role.CASHIER.name());
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer(CustomerStatus.ACTIVE)));
        when(sessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session("OPEN", customerId, businessDate)));
        when(businessDateService.getCurrentBusinessDate()).thenReturn(businessDate);
        when(authenticatedUserService.getRequiredUser()).thenReturn(actor());
        when(walletTransactionService.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(chipCustodyService.recordBuyIn(any(), any(), any(), anyMap(), any(), any()))
                .thenAnswer(invocation -> custodyMovement(invocation.getArgument(3)));
        when(buyInRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsServerOwnedBuyInAndWalletTransaction() {
        ChipBuyInResponse response = service.create(request(PaymentMode.CASH, null));

        assertThat(response.buyInCode()).startsWith("BI-20260808-");
        assertThat(response.businessDate()).isEqualTo(businessDate);
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.createdBy().id()).isEqualTo(actorId);
        verify(businessDateService).validateNewOperationalMutationAllowed();

        verify(walletTransactionService).save(argThat(tx ->
                customerId.equals(tx.getCustomerId())
                        && sessionId.equals(tx.getCustomerSessionId())
                        && "BUY_IN".equals(tx.getTransactionType())
                        && new BigDecimal("10000").compareTo(tx.getAmount()) == 0));
        verify(buyInRepository).save(argThat(buyIn ->
                buyIn.getId() != null
                        && actorId.equals(buyIn.getCreatedBy())
                        && "idem-1".equals(buyIn.getIdempotencyKey())
                        && buyIn.getHighValueAlert() == null
                        && buyIn.getSupervisorApprovedBy() == null));
    }

    @Test void staleBusinessDateBlocksBuyInBeforeWrites() {
        doThrow(new ResourceConflictException("Operational Business Date is stale."))
                .when(businessDateService).validateNewOperationalMutationAllowed();

        assertThatThrownBy(() -> service.create(request(PaymentMode.CASH, null)))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("stale");
        verify(buyInRepository, never()).save(any());
        verifyNoInteractions(walletTransactionService);
    }

    @Test void submittedReconciliationBlocksAllPaymentModesBeforeWrites() {
        doThrow(new ResourceConflictException("Cashier reconciliation has already been submitted for this Business Date."))
                .when(reconciliationService).validatePostingAllowed(actorId, businessDate);
        assertThatThrownBy(() -> service.create(request(PaymentMode.BANK, "BANK-1")))
                .hasMessageContaining("reconciliation has already been submitted");
        verifyNoInteractions(walletTransactionService);
        verify(buyInRepository, never()).save(any());
    }

    @Test
    void returnsExistingBuyInForDuplicateIdempotencyKeyWithoutAnotherWrite() {
        ChipBuyIn existing = existingBuyIn();
        when(buyInRepository.findByIdempotencyKey("idem-1")).thenReturn(Optional.of(existing));
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor()));

        ChipBuyInResponse response = service.create(request(PaymentMode.CASH, null));

        assertThat(response.id()).isEqualTo(existing.getId());
        verify(walletTransactionService, never()).save(any());
        verify(buyInRepository, never()).save(any());
    }

    @Test void reusedIdempotencyKeyWithDifferentPayloadIsRejected() {
        ChipBuyIn existing = existingBuyIn();
        existing.setAmountReceived(new BigDecimal("9000"));
        when(buyInRepository.findByIdempotencyKey("idem-1")).thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> service.create(request(PaymentMode.CASH, null)))
                .hasMessage("Idempotency key has already been used for a different buy-in request.");
        verify(walletTransactionService, never()).save(any());
    }

    @Test void noOpenBusinessDateIsRejected() {
        doThrow(new RuntimeException("Current business date is not opened."))
                .when(businessDateService).validateBusinessDateIsOpen();
        assertThatThrownBy(() -> service.create(request(PaymentMode.CASH, null)))
                .hasMessage("Current business date is not opened.");
        verify(walletTransactionService, never()).save(any());
    }

    @Test void missingCustomerIsRejected() {
        when(customerRepository.findById(customerId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(request(PaymentMode.CASH, null)))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Customer not found.");
    }

    @Test void inactiveCustomerIsRejected() {
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer(CustomerStatus.INACTIVE)));
        assertThatThrownBy(() -> service.create(request(PaymentMode.CASH, null)))
                .hasMessage("Customer must be ACTIVE to create a buy-in.");
    }

    @Test void missingSessionIsRejected() {
        when(sessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(request(PaymentMode.CASH, null)))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Customer session not found.");
    }

    @Test void closedSessionIsRejected() {
        when(sessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session("CLOSED", customerId, businessDate)));
        assertThatThrownBy(() -> service.create(request(PaymentMode.CASH, null)))
                .hasMessage("Customer session must be OPEN.");
    }

    @Test void customerSessionMismatchIsRejected() {
        when(sessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session("OPEN", UUID.randomUUID(), businessDate)));
        assertThatThrownBy(() -> service.create(request(PaymentMode.CASH, null)))
                .hasMessage("Customer session does not belong to the supplied customer.");
    }

    @Test void sessionBusinessDateMismatchIsRejected() {
        when(sessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session("OPEN", customerId, businessDate.minusDays(1))));
        assertThatThrownBy(() -> service.create(request(PaymentMode.CASH, null)))
                .hasMessage("Customer session does not belong to the current OPEN Business Date.");
    }

    @Test void systemLockIsEnforced() {
        when(systemLockService.isSystemLocked()).thenReturn(true);
        assertThatThrownBy(() -> service.create(request(PaymentMode.CASH, null)))
                .hasMessage("System is locked. Buy-In transactions are not allowed.");
    }

    @Test void unequalAmountsAreRejected() {
        CreateChipBuyInRequest request = new CreateChipBuyInRequest(customerId, sessionId,
                new BigDecimal("10000"), PaymentMode.CASH, new BigDecimal("9000"),
                Map.of(1000, 9L), null, null, "idem-1");
        assertThatThrownBy(() -> service.create(request))
                .hasMessage("Amount received must equal total chip value issued.");
    }

    @Test void nonCashPaymentRequiresReference() {
        assertThatThrownBy(() -> service.create(request(PaymentMode.BANK, " ")))
                .hasMessage("Payment reference is required for non-cash payment modes.");
    }

    @Test void walletFailurePreventsBuyInAndAuditWrites() {
        when(walletTransactionService.save(any())).thenThrow(new RuntimeException("wallet failed"));
        assertThatThrownBy(() -> service.create(request(PaymentMode.CASH, null))).hasMessage("wallet failed");
        verify(buyInRepository, never()).save(any());
        verify(auditLogService, never()).log(any(), any(), any(), any(), any());
    }

    @Test void insufficientPhysicalInventoryPreventsAllFinancialWrites() {
        doThrow(new ResourceConflictException("Insufficient physical chips for denomination NPR 1000."))
                .when(chipCustodyService).recordBuyIn(any(), eq(sessionId), eq(businessDate),
                        anyMap(), any(), eq(actorId));

        assertThatThrownBy(() -> service.create(request(PaymentMode.CASH, null)))
                .isInstanceOf(ResourceConflictException.class).hasMessageContaining("Insufficient physical chips");

        verify(walletTransactionService, never()).save(any());
        verify(buyInRepository, never()).save(any());
        verify(auditLogService, never()).log(eq("CREATE_BUY_IN"), any(), any(), any(), any());
    }

    @Test void buyInPersistenceFailureDoesNotWriteAudit() {
        when(buyInRepository.save(any())).thenThrow(new RuntimeException("buy-in persistence failed"));

        assertThatThrownBy(() -> service.create(request(PaymentMode.CASH, null)))
                .hasMessage("buy-in persistence failed");

        verify(walletTransactionService).save(any());
        verify(auditLogService, never()).log(any(), any(), any(), any(), any());
    }

    @Test
    void currentBusinessDateHistoryReturnsOnlyRecordsFromAuthoritativeOpenDate() {
        ChipBuyIn currentDateBuyIn = existingBuyIn();
        currentDateBuyIn.setCreatedAt(LocalDateTime.of(2026, 8, 8, 12, 30));
        Customer customer = customer(CustomerStatus.ACTIVE);
        customer.setCustomerCode("CUS-1001");
        customer.setFullName("Test Customer");
        when(buyInRepository.findByBusinessDateOrderByCreatedAtDesc(businessDate))
                .thenReturn(List.of(currentDateBuyIn));
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor()));

        var history = service.getCurrentBusinessDateHistory();

        assertThat(history).hasSize(1);
        assertThat(history.getFirst().transaction().id()).isEqualTo(currentDateBuyIn.getId());
        assertThat(history.getFirst().transaction().businessDate()).isEqualTo(businessDate);
        assertThat(history.getFirst().customerCode()).isEqualTo("CUS-1001");
        assertThat(history.getFirst().customerName()).isEqualTo("Test Customer");
        verify(buyInRepository).findByBusinessDateOrderByCreatedAtDesc(businessDate);
        verify(buyInRepository, never()).findAll();
    }

    @Test
    void nonCashierRoleCannotReadCurrentBusinessDateHistory() {
        when(currentUserRoleService.getCurrentUserRole()).thenReturn(Role.RECEPTIONIST.name());

        assertThatThrownBy(service::getCurrentBusinessDateHistory)
                .hasMessage("Access denied. Buy-In history is restricted.");

        verifyNoInteractions(businessDateService);
        verify(buyInRepository, never()).findByBusinessDateOrderByCreatedAtDesc(any());
    }

    private CreateChipBuyInRequest request(PaymentMode mode, String reference) {
        return new CreateChipBuyInRequest(customerId, sessionId, new BigDecimal("10000"), mode,
                new BigDecimal("10000"), Map.of(1000, 10L), reference, "Test", "idem-1");
    }

    private Customer customer(CustomerStatus status) {
        Customer customer = new Customer(); customer.setId(customerId); customer.setStatus(status); return customer;
    }

    private CustomerSession session(String status, UUID owner, LocalDate date) {
        CustomerSession session = new CustomerSession(); session.setId(sessionId); session.setStatus(status);
        session.setCustomerId(owner); session.setBusinessDate(date); return session;
    }

    private User actor() {
        User user = new User(); user.setId(actorId); user.setUsername("cashier"); return user;
    }

    private ChipBuyIn existingBuyIn() {
        ChipBuyIn buyIn = new ChipBuyIn(); buyIn.setId(UUID.randomUUID()); buyIn.setBuyInCode("BI-existing");
        buyIn.setCustomerId(customerId); buyIn.setCustomerSessionId(sessionId);
        buyIn.setAmountReceived(new BigDecimal("10000")); buyIn.setTotalChipValueIssued(new BigDecimal("10000"));
        buyIn.setPaymentMode("CASH"); buyIn.setBusinessDate(businessDate); buyIn.setCreatedBy(actorId);
        buyIn.setDenominations(Map.of(1000, 10L));
        return buyIn;
    }

    private ChipCustodyMovement custodyMovement(Map<Integer, Long> denominations) {
        ChipCustodyMovement movement = new ChipCustodyMovement();
        movement.setDenominations(new java.util.LinkedHashMap<>(denominations));
        return movement;
    }
}
