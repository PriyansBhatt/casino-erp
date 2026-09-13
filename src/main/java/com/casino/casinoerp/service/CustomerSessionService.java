package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.ReceptionSessionResponse;
import com.casino.casinoerp.entity.Customer;
import com.casino.casinoerp.entity.CustomerStatus;
import com.casino.casinoerp.entity.CustomerSession;
import com.casino.casinoerp.entity.PitTableCustomerAssignmentStatus;
import com.casino.casinoerp.exception.ResourceConflictException;
import com.casino.casinoerp.exception.ResourceNotFoundException;
import com.casino.casinoerp.repository.CustomerSessionRepository;
import com.casino.casinoerp.repository.PitTableCustomerAssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class CustomerSessionService {

    private final CustomerSessionRepository repository;
    private final BusinessDateService businessDateService;
    private final SystemLockService systemLockService;
    private final AuditLogService auditLogService;
    private final RolePermissionService rolePermissionService;
    private final CurrentUserRoleService currentUserRoleService;
    private final CustomerService customerService;
    private final PitTableCustomerAssignmentRepository pitTableAssignmentRepository;
    private final SessionFinancialPositionService financialPositionService;

    public CustomerSessionService(
            CustomerSessionRepository repository,
            BusinessDateService businessDateService,
            SystemLockService systemLockService,
            AuditLogService auditLogService,
            RolePermissionService rolePermissionService,
            CurrentUserRoleService currentUserRoleService,
            CustomerService customerService,
            PitTableCustomerAssignmentRepository pitTableAssignmentRepository,
            SessionFinancialPositionService financialPositionService) {

        this.repository = repository;
        this.businessDateService = businessDateService;
        this.systemLockService = systemLockService;
        this.auditLogService = auditLogService;
        this.rolePermissionService = rolePermissionService;
        this.currentUserRoleService = currentUserRoleService;
        this.customerService = customerService;
        this.pitTableAssignmentRepository = pitTableAssignmentRepository;
        this.financialPositionService = financialPositionService;
    }

    public List<CustomerSession> getAllSessions() {
        return repository.findAll();
    }

    public List<ReceptionSessionResponse> getAllReceptionSessions() {
        return getAllReceptionSessions(null);
    }

    @Transactional(readOnly = true)
    public List<ReceptionSessionResponse> getAllReceptionSessions(LocalDate businessDate) {
        return (businessDate == null ? repository.findAll()
                : repository.findByBusinessDateOrderByEntryTimeAsc(businessDate))
                .stream()
                .map(this::toReceptionResponse)
                .toList();
    }

    @Transactional
    public ReceptionSessionResponse openSession(UUID customerId) {
        validateSessionRole("Access denied. Only Receptionist or Super Admin can create sessions.");
        businessDateService.validateNewOperationalMutationAllowed();

        Customer customer = customerService.getRequiredCustomer(customerId);
        validateCustomerStatus(customer);

        if (repository.existsByCustomerIdAndStatusIgnoreCase(customerId, "OPEN")) {
            throw new RuntimeException("Customer already has an active session.");
        }

        businessDateService.validateBusinessDateIsOpen();

        if (systemLockService.isSystemLocked()) {
            throw new RuntimeException(
                    "System is locked. Customer sessions are not allowed."
            );
        }

        LocalDate businessDate = businessDateService.getCurrentBusinessDate();
        LocalDateTime now = LocalDateTime.now();
        UUID sessionId = UUID.randomUUID();

        CustomerSession session = new CustomerSession();
        session.setId(sessionId);
        session.setSessionCode(generateSessionCode(businessDate, sessionId));
        session.setCustomerId(customerId);
        session.setSessionDate(businessDate);
        session.setEntryTime(now);
        session.setStatus("OPEN");
        session.setOpenedBy(currentUserRoleService.getCurrentUserId());
        session.setBusinessDate(businessDate);
        session.setCreatedAt(now);

        CustomerSession saved = repository.save(session);

        auditLogService.log(
                "CREATE_SESSION",
                "CUSTOMER_SESSION",
                saved.getId(),
                currentUserRoleService.getCurrentUserId(),
                "Customer session created: " + saved.getSessionCode()
        );

        return toReceptionResponse(saved);
    }
    
    @Transactional
    public ReceptionSessionResponse closeSession(UUID sessionId) {
        validateSessionRole("Access denied. Only Receptionist or Super Admin can close sessions.");
        businessDateService.validateSettlementMutationAllowed();

        businessDateService.validateBusinessDateIsOpen();

        if (systemLockService.isSystemLocked()) {
            throw new RuntimeException(
                    "System is locked. Customer sessions cannot be closed."
            );
        }

        CustomerSession session = repository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer session not found."));

        if ("CLOSED".equalsIgnoreCase(session.getStatus())) {
            throw new RuntimeException("Customer session is already CLOSED.");
        }

        if (!"OPEN".equalsIgnoreCase(session.getStatus())) {
            throw new RuntimeException("Only an OPEN customer session can be closed.");
        }

        if (pitTableAssignmentRepository.findByCustomerSessionIdAndStatus(
                sessionId, PitTableCustomerAssignmentStatus.ACTIVE).isPresent()) {
            throw new ResourceConflictException(
                    "Customer must leave and settle the active Pit Table assignment before the session can be closed.");
        }

        BigDecimal chipPosition = financialPositionService.getPosition(sessionId).calculatedChipPosition();
        if (chipPosition.compareTo(BigDecimal.ZERO) > 0) {
            throw new ResourceConflictException(
                    "Customer session has outstanding chips that must be settled or cashed out before closure.");
        }
        if (chipPosition.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResourceConflictException(
                    "Customer session has an inconsistent negative chip position that must be resolved before closure.");
        }

        session.setStatus("CLOSED");
        session.setExitTime(LocalDateTime.now());
        session.setClosedBy(currentUserRoleService.getCurrentUserId());

        CustomerSession saved = repository.save(session);

        auditLogService.log(
                "CLOSE_SESSION",
                "CUSTOMER_SESSION",
                saved.getId(),
                currentUserRoleService.getCurrentUserId(),
                "Customer session closed: " + saved.getSessionCode()
        );

        return toReceptionResponse(saved);
    }

    public ReceptionSessionResponse getActiveSession(UUID customerId) {
        customerService.getRequiredCustomer(customerId);

        CustomerSession session = repository
                .findFirstByCustomerIdAndStatusIgnoreCaseAndExitTimeIsNull(customerId, "OPEN")
                .orElseThrow(() -> new ResourceNotFoundException("Active customer session not found."));

        return toReceptionResponse(session);
    }

    private void validateSessionRole(String message) {
        if (!currentUserRoleService.getCurrentRole()
                .map(rolePermissionService::canCreateSession)
                .orElse(false)) {
            throw new RuntimeException(message);
        }
    }

    private void validateCustomerStatus(Customer customer) {
        if (customer.getStatus() == null) {
            return;
        }

        CustomerStatus status = customer.getStatus();
        if (status == CustomerStatus.BLOCKED || status == CustomerStatus.INACTIVE) {
            throw new RuntimeException(
                    "Customer status " + status + " does not allow an active session."
            );
        }
    }

    private String generateSessionCode(LocalDate businessDate, UUID sessionId) {
        return "SES-"
                + businessDate.toString().replace("-", "")
                + "-"
                + sessionId.toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private ReceptionSessionResponse toReceptionResponse(CustomerSession session) {
        return new ReceptionSessionResponse(
                session.getId(),
                session.getSessionCode(),
                session.getCustomerId(),
                session.getSessionDate(),
                session.getEntryTime(),
                session.getExitTime(),
                session.getStatus(),
                session.getOpenedBy(),
                session.getClosedBy(),
                session.getBusinessDate(),
                session.getRemarks()
        );
    }

}
