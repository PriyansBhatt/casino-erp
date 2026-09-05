package com.casino.casinoerp;

import com.casino.casinoerp.dto.AssignPitTableCustomerRequest;
import com.casino.casinoerp.dto.LeavePitTableCustomerRequest;
import com.casino.casinoerp.dto.SessionFinancialPositionResponse;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.exception.ResourceConflictException;
import com.casino.casinoerp.exception.ResourceNotFoundException;
import com.casino.casinoerp.repository.*;
import com.casino.casinoerp.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PitTableCustomerAssignmentServiceTests {
    private final PitTableCustomerAssignmentRepository repository = mock(PitTableCustomerAssignmentRepository.class);
    private final PitTableRepository tableRepository = mock(PitTableRepository.class);
    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final CustomerSessionRepository sessionRepository = mock(CustomerSessionRepository.class);
    private final BusinessDateService businessDateService = mock(BusinessDateService.class);
    private final SystemLockService systemLockService = mock(SystemLockService.class);
    private final CurrentUserRoleService roleService = mock(CurrentUserRoleService.class);
    private final AuthenticatedUserService userService = mock(AuthenticatedUserService.class);
    private final AuditLogService auditService = mock(AuditLogService.class);
    private final ChipCustodyService custodyService = mock(ChipCustodyService.class);
    private final SessionFinancialPositionService financialService = mock(SessionFinancialPositionService.class);
    private final PitTableCustomerAssignmentService service = new PitTableCustomerAssignmentService(
            repository, tableRepository, customerRepository, sessionRepository, businessDateService,
            systemLockService, roleService, new RolePermissionService(), userService, auditService,
            custodyService, financialService);

    private final UUID tableId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final LocalDate businessDate = LocalDate.of(2026, 8, 8);
    private final AssignPitTableCustomerRequest request = new AssignPitTableCustomerRequest(customerId, sessionId);

    @BeforeEach void setUp() {
        when(roleService.getCurrentUserRole()).thenReturn("PIT_SUPERVISOR");
        when(businessDateService.getCurrentBusinessDate()).thenReturn(businessDate);
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer(CustomerStatus.ACTIVE)));
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session("OPEN", customerId, businessDate)));
        when(sessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session("OPEN", customerId, businessDate)));
        when(tableRepository.findById(tableId)).thenReturn(Optional.of(table("OPEN", businessDate)));
        when(tableRepository.findByIdForUpdate(tableId)).thenReturn(Optional.of(table("OPEN", businessDate)));
        when(repository.findByCustomerSessionIdAndStatus(sessionId, PitTableCustomerAssignmentStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(userService.getRequiredUser()).thenReturn(actor());
        when(financialService.getPosition(sessionId)).thenReturn(new SessionFinancialPositionResponse(
                customerId, sessionId, businessDate, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        when(repository.save(any())).thenAnswer(call -> {
            PitTableCustomerAssignment value = call.getArgument(0);
            if (value.getId() == null) value.setId(UUID.randomUUID());
            return value;
        });
    }

    @Test void assignsEligibleCustomerAndAudits() {
        var response = service.assign(tableId, request);
        assertThat(response.customerId()).isEqualTo(customerId);
        assertThat(response.pitTableId()).isEqualTo(tableId);
        assertThat(response.status()).isEqualTo(PitTableCustomerAssignmentStatus.ACTIVE);
        verify(auditService).log(eq("ASSIGN_PIT_TABLE_CUSTOMER"), any(), any(), eq(actorId), any());
    }

    @Test void missingCustomerRejected() {
        when(customerRepository.findById(customerId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.assign(tableId, request))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Customer not found.");
    }

    @Test void inactiveCustomerRejected() {
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer(CustomerStatus.BLOCKED)));
        assertThatThrownBy(() -> service.assign(tableId, request)).hasMessageContaining("must be ACTIVE");
    }

    @Test void wrongCustomerSessionRejected() {
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session("OPEN", UUID.randomUUID(), businessDate)));
        assertThatThrownBy(() -> service.assign(tableId, request)).hasMessageContaining("does not belong");
    }

    @Test void closedOrWrongDateSessionRejected() {
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session("CLOSED", customerId, businessDate)));
        assertThatThrownBy(() -> service.assign(tableId, request)).hasMessage("Customer session must be OPEN.");
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session("OPEN", customerId, businessDate.minusDays(1))));
        assertThatThrownBy(() -> service.assign(tableId, request)).hasMessageContaining("current OPEN Business Date");
    }

    @Test void closedTableRejected() {
        when(tableRepository.findById(tableId)).thenReturn(Optional.of(table("CLOSED", businessDate)));
        assertThatThrownBy(() -> service.assign(tableId, request)).hasMessage("Pit table must be OPEN.");
    }

    @Test void duplicateActiveAssignmentRejected() {
        PitTableCustomerAssignment existing = assignment(PitTableCustomerAssignmentStatus.ACTIVE);
        when(repository.findByCustomerSessionIdAndStatus(sessionId, PitTableCustomerAssignmentStatus.ACTIVE))
                .thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> service.assign(tableId, request)).isInstanceOf(ResourceConflictException.class);
    }

    @Test void systemLockRejected() {
        when(systemLockService.isSystemLocked()).thenReturn(true);
        assertThatThrownBy(() -> service.assign(tableId, request)).hasMessageContaining("System is locked");
        verify(repository, never()).save(any());
    }

    @Test void leaveClosesButDoesNotDeleteAssignment() {
        PitTableCustomerAssignment active = assignment(PitTableCustomerAssignmentStatus.ACTIVE);
        when(repository.findByIdForUpdate(active.getId())).thenReturn(Optional.of(active));
        when(repository.findById(active.getId())).thenReturn(Optional.of(active));
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer(CustomerStatus.ACTIVE)));
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session("OPEN", customerId, businessDate)));

        var response = service.leave(tableId, active.getId(),
                new LeavePitTableCustomerRequest(Map.of(), "leave-key"));

        assertThat(response.status()).isEqualTo(PitTableCustomerAssignmentStatus.LEFT);
        assertThat(response.leftAt()).isNotNull();
        verify(repository).save(active);
        verify(custodyService).settleAssignmentForLeave(eq(active), eq(Map.of()),
                eq("ASSIGNMENT_SETTLEMENT:leave-key"), eq(actorId));
        verify(repository, never()).delete(any());
    }

    @Test void leaveSettlementFailureDoesNotEndAssignment() {
        PitTableCustomerAssignment active = assignment(PitTableCustomerAssignmentStatus.ACTIVE);
        when(repository.findByIdForUpdate(active.getId())).thenReturn(Optional.of(active));
        when(repository.findById(active.getId())).thenReturn(Optional.of(active));
        doThrow(new ResourceConflictException("Insufficient physical chips"))
                .when(custodyService).settleAssignmentForLeave(any(), any(), any(), any());

        assertThatThrownBy(() -> service.leave(tableId, active.getId(),
                new LeavePitTableCustomerRequest(Map.of(5000, 2L), "leave-fail")))
                .hasMessageContaining("Insufficient physical chips");
        assertThat(active.getStatus()).isEqualTo(PitTableCustomerAssignmentStatus.ACTIVE);
        verify(repository, never()).save(active);
    }

    private Customer customer(CustomerStatus status) {
        Customer value = new Customer(); value.setId(customerId); value.setCustomerCode("CUS-1001");
        value.setFullName("Test Customer"); value.setStatus(status); return value;
    }
    private CustomerSession session(String status, UUID owner, LocalDate date) {
        CustomerSession value = new CustomerSession(); value.setId(sessionId); value.setCustomerId(owner);
        value.setSessionCode("SES-001"); value.setStatus(status); value.setBusinessDate(date); return value;
    }
    private PitTable table(String status, LocalDate date) {
        PitTable value = new PitTable(); value.setId(tableId); value.setStatus(status); value.setBusinessDate(date); return value;
    }
    private User actor() {
        User value = new User(); value.setId(actorId); value.setUsername("pitboss"); return value;
    }
    private PitTableCustomerAssignment assignment(PitTableCustomerAssignmentStatus status) {
        PitTableCustomerAssignment value = new PitTableCustomerAssignment(); value.setId(UUID.randomUUID());
        value.setPitTableId(tableId); value.setCustomerId(customerId); value.setCustomerSessionId(sessionId);
        value.setBusinessDate(businessDate); value.setStatus(status); value.setJoinedAt(java.time.LocalDateTime.now());
        value.setJoinedBy(actorId); return value;
    }
}
