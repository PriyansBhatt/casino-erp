package com.casino.casinoerp;

import com.casino.casinoerp.dto.CreateVerifiedGamingResultRequest;
import com.casino.casinoerp.dto.VerifiedGamingResultResponse;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.exception.ResourceNotFoundException;
import com.casino.casinoerp.repository.*;
import com.casino.casinoerp.security.Role;
import com.casino.casinoerp.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VerifiedGamingResultServiceTests {
    private final VerifiedGamingResultRepository repository = mock(VerifiedGamingResultRepository.class);
    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final CustomerSessionRepository sessionRepository = mock(CustomerSessionRepository.class);
    private final PitTableRepository tableRepository = mock(PitTableRepository.class);
    private final PitTableCustomerAssignmentRepository assignmentRepository = mock(PitTableCustomerAssignmentRepository.class);
    private final BusinessDateService businessDateService = mock(BusinessDateService.class);
    private final SystemLockService systemLockService = mock(SystemLockService.class);
    private final CurrentUserRoleService currentUserRoleService = mock(CurrentUserRoleService.class);
    private final AuthenticatedUserService authenticatedUserService = mock(AuthenticatedUserService.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final PitTableAccessService tableAccess = mock(PitTableAccessService.class);
    private final VerifiedGamingResultService service = new VerifiedGamingResultService(
            repository, customerRepository, sessionRepository, tableRepository, assignmentRepository, businessDateService,
            systemLockService, currentUserRoleService, new RolePermissionService(),
            authenticatedUserService, auditLogService, tableAccess);

    private final UUID customerId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID tableId = UUID.randomUUID();
    private final UUID assignmentId = UUID.randomUUID();
    private final LocalDate businessDate = LocalDate.of(2026, 8, 8);

    @BeforeEach
    void setUp() {
        when(currentUserRoleService.getCurrentRole()).thenReturn(Optional.of(Role.PIT_SUPERVISOR));
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer(CustomerStatus.ACTIVE)));
        when(sessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session("OPEN", customerId, businessDate)));
        when(tableRepository.findByIdForUpdate(tableId)).thenReturn(Optional.of(table("OPEN", businessDate)));
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment()));
        when(repository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(businessDateService.getCurrentBusinessDate()).thenReturn(businessDate);
        when(authenticatedUserService.getRequiredUser()).thenReturn(actor());
        when(repository.save(any())).thenAnswer(invocation -> {
            VerifiedGamingResult result = invocation.getArgument(0);
            result.setId(UUID.randomUUID());
            return result;
        });
    }

    @Test
    void validResultUsesServerOwnedContextAndPersists() {
        VerifiedGamingResultResponse response = service.create(request(new BigDecimal("2500")));

        assertThat(response.id()).isNotNull();
        assertThat(response.businessDate()).isEqualTo(businessDate);
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.createdBy().id()).isEqualTo(actorId);
        assertThat(response.denominations()).containsEntry(500, 5);
        assertThat(response.amount()).isEqualByComparingTo("2500");
        verify(repository).save(argThat(result -> businessDate.equals(result.getBusinessDate())
                && actorId.equals(result.getCreatedBy())
                && result.getCreatedAt() != null));
        verify(auditLogService).log(eq("CREATE_VERIFIED_GAMING_RESULT"), any(), any(), eq(actorId), any());
    }

    @Test void missingCustomerRejected() {
        when(customerRepository.findById(customerId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(request(BigDecimal.ONE)))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Customer not found.");
    }

    @Test void inactiveCustomerRejected() {
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer(CustomerStatus.INACTIVE)));
        assertThatThrownBy(() -> service.create(request(BigDecimal.ONE)))
                .hasMessage("Customer must be ACTIVE to record a verified gaming result.");
    }

    @Test void missingSessionRejected() {
        when(sessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(request(BigDecimal.ONE)))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Customer session not found.");
    }

    @Test void wrongCustomerSessionRejected() {
        when(sessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session("OPEN", UUID.randomUUID(), businessDate)));
        assertThatThrownBy(() -> service.create(request(BigDecimal.ONE)))
                .hasMessage("Customer session does not belong to the supplied customer.");
    }

    @Test void closedSessionRejected() {
        when(sessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session("CLOSED", customerId, businessDate)));
        assertThatThrownBy(() -> service.create(request(BigDecimal.ONE)))
                .hasMessage("Customer session must be OPEN.");
    }

    @Test void closedTableRejected() {
        when(tableRepository.findByIdForUpdate(tableId)).thenReturn(Optional.of(table("CLOSED", businessDate)));
        assertThatThrownBy(() -> service.create(request(BigDecimal.ONE)))
                .hasMessage("Pit table must be OPEN.");
        verify(repository, never()).save(any());
    }

    @Test void wrongBusinessDateRejected() {
        when(sessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session("OPEN", customerId, businessDate.minusDays(1))));
        assertThatThrownBy(() -> service.create(request(BigDecimal.ONE)))
                .hasMessage("Customer session does not belong to the current OPEN Business Date.");
    }

    @Test void systemLockRejected() {
        when(systemLockService.isSystemLocked()).thenReturn(true);
        assertThatThrownBy(() -> service.create(request(BigDecimal.ONE)))
                .hasMessage("System is locked. Verified gaming results are not allowed.");
    }

    @Test void cashierCannotRecordResult() {
        when(currentUserRoleService.getCurrentRole()).thenReturn(Optional.of(Role.CASHIER));
        assertThatThrownBy(() -> service.create(request(BigDecimal.ONE)))
                .hasMessageContaining("Only Dealer, Pit Supervisor or Super Admin");
        verify(repository, never()).save(any());
    }

    @Test void persistenceFailureDoesNotWriteAudit() {
        doThrow(new RuntimeException("persistence failed")).when(repository).save(any());
        assertThatThrownBy(() -> service.create(request(BigDecimal.ONE))).hasMessage("persistence failed");
        verify(auditLogService, never()).log(any(), any(), any(), any(), any());
    }

    @Test void matchingIdempotencyReplayReturnsExistingResultWithoutAnotherInsert() {
        VerifiedGamingResult existing = existingResult();
        when(repository.findByIdempotencyKey("test-key-2500")).thenReturn(Optional.of(existing));

        VerifiedGamingResultResponse response = service.create(request(new BigDecimal("2500")));

        assertThat(response.id()).isEqualTo(existing.getId());
        verify(repository, never()).save(any());
        verify(auditLogService, never()).log(any(), any(), any(), any(), any());
    }

    @Test void assignmentForAnotherTableIsRejected() {
        PitTableCustomerAssignment assignment = assignment();
        assignment.setPitTableId(UUID.randomUUID());
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> service.create(request(BigDecimal.ONE)))
                .hasMessage("Customer does not have a matching ACTIVE assignment to this Pit Table.");
        verify(repository, never()).save(any());
    }

    @Test void unsupportedDenominationRejected() {
        assertThatThrownBy(() -> service.create(request(Map.of(2000, 1), null, "unsupported")))
                .hasMessageContaining("Unsupported chip denomination");
    }

    @Test void negativeAndZeroQuantitiesRejected() {
        assertThatThrownBy(() -> service.create(request(Map.of(500, -1), null, "negative")))
                .hasMessageContaining("non-negative integers");
        assertThatThrownBy(() -> service.create(request(Map.of(500, 0), null, "zero")))
                .hasMessageContaining("At least one denomination");
    }

    @Test void inconsistentClientAmountRejected() {
        assertThatThrownBy(() -> service.create(request(Map.of(1000, 2), new BigDecimal("1999"), "mismatch")))
                .hasMessage("Supplied amount does not match the denomination total.");
        verify(repository, never()).save(any());
    }

    @Test void lossAmountIsCalculatedFromDenominations() {
        CreateVerifiedGamingResultRequest loss = new CreateVerifiedGamingResultRequest(
                customerId, sessionId, tableId, assignmentId, VerifiedGamingSourceType.TABLE,
                VerifiedGamingResultType.LOSS, Map.of(10000, 1, 5000, 2), null, "loss-key");
        VerifiedGamingResultResponse response = service.create(loss);
        assertThat(response.resultType()).isEqualTo(VerifiedGamingResultType.LOSS);
        assertThat(response.amount()).isEqualByComparingTo("20000");
        assertThat(response.denominations()).containsExactlyInAnyOrderEntriesOf(Map.of(10000, 1, 5000, 2));
    }

    private CreateVerifiedGamingResultRequest request(BigDecimal amount) {
        int quantity = amount.compareTo(new BigDecimal("2500")) == 0 ? 5 : 1;
        return request(Map.of(500, quantity), null,
                "test-key-" + amount);
    }

    private CreateVerifiedGamingResultRequest request(
            Map<Integer, Integer> denominations, BigDecimal amount, String key) {
        return new CreateVerifiedGamingResultRequest(customerId, sessionId, tableId, assignmentId,
                VerifiedGamingSourceType.TABLE, VerifiedGamingResultType.WIN,
                denominations, amount, key);
    }

    private Customer customer(CustomerStatus status) {
        Customer customer = new Customer(); customer.setId(customerId); customer.setStatus(status); return customer;
    }

    private CustomerSession session(String status, UUID owner, LocalDate date) {
        CustomerSession session = new CustomerSession(); session.setId(sessionId); session.setCustomerId(owner);
        session.setStatus(status); session.setBusinessDate(date); return session;
    }

    private User actor() {
        User user = new User(); user.setId(actorId); user.setUsername("pitboss"); return user;
    }

    private PitTable table(String status, LocalDate date) {
        PitTable table = new PitTable(); table.setId(tableId); table.setStatus(status);
        table.setBusinessDate(date); return table;
    }

    private PitTableCustomerAssignment assignment() {
        PitTableCustomerAssignment assignment = new PitTableCustomerAssignment();
        assignment.setId(assignmentId); assignment.setPitTableId(tableId);
        assignment.setCustomerId(customerId); assignment.setCustomerSessionId(sessionId);
        assignment.setBusinessDate(businessDate);
        assignment.setStatus(PitTableCustomerAssignmentStatus.ACTIVE);
        return assignment;
    }

    private VerifiedGamingResult existingResult() {
        VerifiedGamingResult result = new VerifiedGamingResult();
        result.setId(UUID.randomUUID()); result.setCustomerId(customerId);
        result.setCustomerSessionId(sessionId); result.setPitTableId(tableId);
        result.setAssignmentId(assignmentId); result.setBusinessDate(businessDate);
        result.setSourceType(VerifiedGamingSourceType.TABLE);
        result.setResultType(VerifiedGamingResultType.WIN);
        result.setAmount(new BigDecimal("2500")); result.setIdempotencyKey("test-key-2500");
        result.setDenominations(Map.of(500, 5));
        result.setCreatedAt(java.time.LocalDateTime.now()); result.setCreatedBy(actorId);
        return result;
    }
}
