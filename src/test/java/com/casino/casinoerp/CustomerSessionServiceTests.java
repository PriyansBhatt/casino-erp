package com.casino.casinoerp;

import com.casino.casinoerp.dto.ReceptionSessionResponse;
import com.casino.casinoerp.dto.SessionFinancialPositionResponse;
import com.casino.casinoerp.entity.Customer;
import com.casino.casinoerp.entity.CustomerStatus;
import com.casino.casinoerp.entity.CustomerSession;
import com.casino.casinoerp.entity.PitTableCustomerAssignment;
import com.casino.casinoerp.entity.PitTableCustomerAssignmentStatus;
import com.casino.casinoerp.exception.ResourceNotFoundException;
import com.casino.casinoerp.exception.ResourceConflictException;
import com.casino.casinoerp.repository.CustomerSessionRepository;
import com.casino.casinoerp.repository.PitTableCustomerAssignmentRepository;
import com.casino.casinoerp.security.Role;
import com.casino.casinoerp.service.AuditLogService;
import com.casino.casinoerp.service.BusinessDateService;
import com.casino.casinoerp.service.CurrentUserRoleService;
import com.casino.casinoerp.service.CustomerService;
import com.casino.casinoerp.service.CustomerSessionService;
import com.casino.casinoerp.service.RolePermissionService;
import com.casino.casinoerp.service.SessionFinancialPositionService;
import com.casino.casinoerp.service.SystemLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerSessionServiceTests {

    private final CustomerSessionRepository repository = mock(CustomerSessionRepository.class);
    private final BusinessDateService businessDateService = mock(BusinessDateService.class);
    private final SystemLockService systemLockService = mock(SystemLockService.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final RolePermissionService rolePermissionService = new RolePermissionService();
    private final CurrentUserRoleService currentUserRoleService = mock(CurrentUserRoleService.class);
    private final CustomerService customerService = mock(CustomerService.class);
    private final PitTableCustomerAssignmentRepository pitTableAssignmentRepository =
            mock(PitTableCustomerAssignmentRepository.class);
    private final SessionFinancialPositionService financialPositionService =
            mock(SessionFinancialPositionService.class);
    private final CustomerSessionService service = new CustomerSessionService(
            repository,
            businessDateService,
            systemLockService,
            auditLogService,
            rolePermissionService,
            currentUserRoleService,
            customerService,
            pitTableAssignmentRepository,
            financialPositionService
    );

    private final UUID customerId = UUID.randomUUID();
    private final UUID operatorId = UUID.randomUUID();
    private final LocalDate businessDate = LocalDate.of(2026, 8, 8);

    @BeforeEach
    void allowReceptionSessionOperations() {
        when(currentUserRoleService.getCurrentRole()).thenReturn(Optional.of(Role.RECEPTIONIST));
        when(currentUserRoleService.getCurrentUserId()).thenReturn(operatorId);
        when(customerService.getRequiredCustomer(customerId)).thenReturn(activeCustomer());
        when(businessDateService.getCurrentBusinessDate()).thenReturn(businessDate);
        when(repository.save(any(CustomerSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void opensSessionWithServerGeneratedOperationalValues() {
        ReceptionSessionResponse response = service.openSession(customerId);

        ArgumentCaptor<CustomerSession> captor = ArgumentCaptor.forClass(CustomerSession.class);
        verify(repository).save(captor.capture());
        CustomerSession saved = captor.getValue();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getSessionCode()).startsWith("SES-20260808-");
        assertThat(saved.getCustomerId()).isEqualTo(customerId);
        assertThat(saved.getSessionDate()).isEqualTo(businessDate);
        assertThat(saved.getBusinessDate()).isEqualTo(businessDate);
        assertThat(saved.getEntryTime()).isNotNull();
        assertThat(saved.getCreatedAt()).isEqualTo(saved.getEntryTime());
        assertThat(saved.getStatus()).isEqualTo("OPEN");
        assertThat(saved.getOpenedBy()).isEqualTo(operatorId);
        assertThat(response.id()).isEqualTo(saved.getId());
        assertThat(response.businessDate()).isEqualTo(businessDate);
        verify(businessDateService).validateNewOperationalMutationAllowed();
        verify(businessDateService).validateBusinessDateIsOpen();
    }

    @Test
    void staleBusinessDateBlocksOpeningSessionBeforePersistence() {
        org.mockito.Mockito.doThrow(new ResourceConflictException("Operational Business Date is stale."))
                .when(businessDateService).validateNewOperationalMutationAllowed();

        assertThatThrownBy(() -> service.openSession(customerId))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("stale");
        verify(repository, never()).save(any());
    }

    @Test
    void unknownCustomerCannotOpenSession() {
        when(customerService.getRequiredCustomer(customerId))
                .thenThrow(new ResourceNotFoundException("Customer not found."));

        assertThatThrownBy(() -> service.openSession(customerId))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void calculatedBusinessDateMustBeOpen() {
        org.mockito.Mockito.doThrow(new RuntimeException("Current business date is not opened."))
                .when(businessDateService).validateBusinessDateIsOpen();

        assertThatThrownBy(() -> service.openSession(customerId))
                .hasMessage("Current business date is not opened.");
        verify(repository, never()).save(any());
    }

    @Test
    void duplicateActiveSessionIsRejected() {
        when(repository.existsByCustomerIdAndStatusIgnoreCase(customerId, "OPEN")).thenReturn(true);

        assertThatThrownBy(() -> service.openSession(customerId))
                .hasMessage("Customer already has an active session.");
        verify(repository, never()).save(any());
    }

    @Test
    void systemLockStillPreventsOpeningSession() {
        when(systemLockService.isSystemLocked()).thenReturn(true);

        assertThatThrownBy(() -> service.openSession(customerId))
                .hasMessage("System is locked. Customer sessions are not allowed.");
        verify(repository, never()).save(any());
    }

    @Test
    void findsActiveSessionUsingSafeResponse() {
        CustomerSession activeSession = openSessionEntity(UUID.randomUUID());
        when(repository.findFirstByCustomerIdAndStatusIgnoreCase(customerId, "OPEN"))
                .thenReturn(Optional.of(activeSession));

        ReceptionSessionResponse response = service.getActiveSession(customerId);

        assertThat(response.id()).isEqualTo(activeSession.getId());
        assertThat(response.status()).isEqualTo("OPEN");
        assertThat(response.businessDate()).isEqualTo(businessDate);
    }

    @Test
    void alreadyClosedSessionCannotCloseAgain() {
        CustomerSession closedSession = openSessionEntity(UUID.randomUUID());
        closedSession.setStatus("CLOSED");
        when(repository.findByIdForUpdate(closedSession.getId())).thenReturn(Optional.of(closedSession));

        assertThatThrownBy(() -> service.closeSession(closedSession.getId()))
                .hasMessage("Customer session is already CLOSED.");
        verify(repository, never()).save(any());
    }

    @Test
    void zeroPositionWithoutActivePitAssignmentAllowsClosure() {
        CustomerSession session = openSessionEntity(UUID.randomUUID());
        stubClosablePosition(session, BigDecimal.ZERO);

        ReceptionSessionResponse response = service.closeSession(session.getId());

        assertThat(response.status()).isEqualTo("CLOSED");
        assertThat(session.getExitTime()).isNotNull();
        assertThat(session.getClosedBy()).isEqualTo(operatorId);
        verify(businessDateService).validateSettlementMutationAllowed();
        verify(repository).save(session);
    }

    @Test
    void positiveChipPositionPreventsClosure() {
        CustomerSession session = openSessionEntity(UUID.randomUUID());
        stubClosablePosition(session, new BigDecimal("5000"));

        assertThatThrownBy(() -> service.closeSession(session.getId()))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("outstanding chips");
        verify(repository, never()).save(any());
    }

    @Test
    void negativeChipPositionPreventsClosure() {
        CustomerSession session = openSessionEntity(UUID.randomUUID());
        stubClosablePosition(session, new BigDecimal("-500"));

        assertThatThrownBy(() -> service.closeSession(session.getId()))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("negative chip position");
        verify(repository, never()).save(any());
    }

    @Test
    void activePitAssignmentPreventsClosureBeforeFinancialCheck() {
        CustomerSession session = openSessionEntity(UUID.randomUUID());
        when(repository.findByIdForUpdate(session.getId())).thenReturn(Optional.of(session));
        when(pitTableAssignmentRepository.findByCustomerSessionIdAndStatus(
                session.getId(), PitTableCustomerAssignmentStatus.ACTIVE))
                .thenReturn(Optional.of(new PitTableCustomerAssignment()));

        assertThatThrownBy(() -> service.closeSession(session.getId()))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("active Pit Table assignment");
        verify(financialPositionService, never()).getPosition(any());
        verify(repository, never()).save(any());
    }

    @Test
    void closureSucceedsAfterTableAssignmentLeavesAndPositionIsSettled() {
        CustomerSession session = openSessionEntity(UUID.randomUUID());
        stubClosablePosition(session, BigDecimal.ZERO);

        ReceptionSessionResponse response = service.closeSession(session.getId());

        assertThat(response.status()).isEqualTo("CLOSED");
        verify(pitTableAssignmentRepository).findByCustomerSessionIdAndStatus(
                session.getId(), PitTableCustomerAssignmentStatus.ACTIVE);
        verify(financialPositionService).getPosition(session.getId());
        verify(repository).save(session);
    }

    private void stubClosablePosition(CustomerSession session, BigDecimal chipPosition) {
        when(repository.findByIdForUpdate(session.getId())).thenReturn(Optional.of(session));
        when(pitTableAssignmentRepository.findByCustomerSessionIdAndStatus(
                session.getId(), PitTableCustomerAssignmentStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(financialPositionService.getPosition(session.getId())).thenReturn(
                new SessionFinancialPositionResponse(customerId, session.getId(), businessDate,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        chipPosition));
    }

    private Customer activeCustomer() {
        Customer customer = new Customer();
        customer.setId(customerId);
        customer.setStatus(CustomerStatus.ACTIVE);
        return customer;
    }

    private CustomerSession openSessionEntity(UUID sessionId) {
        CustomerSession session = new CustomerSession();
        session.setId(sessionId);
        session.setSessionCode("SES-20260808-TEST");
        session.setCustomerId(customerId);
        session.setSessionDate(businessDate);
        session.setBusinessDate(businessDate);
        session.setStatus("OPEN");
        return session;
    }
}
