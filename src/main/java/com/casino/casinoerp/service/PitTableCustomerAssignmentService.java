package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.AssignPitTableCustomerRequest;
import com.casino.casinoerp.dto.PitTablePlayerResponse;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.exception.ResourceConflictException;
import com.casino.casinoerp.exception.ResourceNotFoundException;
import com.casino.casinoerp.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PitTableCustomerAssignmentService {
    private final PitTableCustomerAssignmentRepository repository;
    private final PitTableRepository tableRepository;
    private final CustomerRepository customerRepository;
    private final CustomerSessionRepository sessionRepository;
    private final BusinessDateService businessDateService;
    private final SystemLockService systemLockService;
    private final CurrentUserRoleService currentUserRoleService;
    private final RolePermissionService rolePermissionService;
    private final AuthenticatedUserService authenticatedUserService;
    private final AuditLogService auditLogService;

    public PitTableCustomerAssignmentService(
            PitTableCustomerAssignmentRepository repository, PitTableRepository tableRepository,
            CustomerRepository customerRepository, CustomerSessionRepository sessionRepository,
            BusinessDateService businessDateService, SystemLockService systemLockService,
            CurrentUserRoleService currentUserRoleService, RolePermissionService rolePermissionService,
            AuthenticatedUserService authenticatedUserService, AuditLogService auditLogService) {
        this.repository = repository;
        this.tableRepository = tableRepository;
        this.customerRepository = customerRepository;
        this.sessionRepository = sessionRepository;
        this.businessDateService = businessDateService;
        this.systemLockService = systemLockService;
        this.currentUserRoleService = currentUserRoleService;
        this.rolePermissionService = rolePermissionService;
        this.authenticatedUserService = authenticatedUserService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public PitTablePlayerResponse assign(UUID tableId, AssignPitTableCustomerRequest request) {
        validateActorAndOperations("assign customers to Pit Tables");
        LocalDate businessDate = validateOpenBusinessDateAndLock();
        Customer customer = requiredActiveCustomer(request.customerId());
        CustomerSession session = requiredOpenSession(request.customerSessionId(), customer.getId(), businessDate);
        requiredOpenTable(tableId, businessDate);

        repository.findByCustomerSessionIdAndStatus(
                        session.getId(), PitTableCustomerAssignmentStatus.ACTIVE)
                .ifPresent(existing -> {
                    throw new ResourceConflictException(existing.getPitTableId().equals(tableId)
                            ? "Customer session is already assigned to this table."
                            : "Customer session is already assigned to another active table.");
                });

        User actor = authenticatedUserService.getRequiredUser();
        PitTableCustomerAssignment assignment = new PitTableCustomerAssignment();
        assignment.setPitTableId(tableId);
        assignment.setCustomerId(customer.getId());
        assignment.setCustomerSessionId(session.getId());
        assignment.setBusinessDate(businessDate);
        assignment.setStatus(PitTableCustomerAssignmentStatus.ACTIVE);
        assignment.setJoinedAt(LocalDateTime.now());
        assignment.setJoinedBy(actor.getId());
        PitTableCustomerAssignment saved = repository.save(assignment);
        auditLogService.log("ASSIGN_PIT_TABLE_CUSTOMER", "PIT_TABLE_CUSTOMER_ASSIGNMENT",
                saved.getId(), actor.getId(), "Customer assigned to Pit Table " + tableId);
        return toResponse(saved, customer, session);
    }

    @Transactional
    public PitTablePlayerResponse leave(UUID tableId, UUID assignmentId) {
        validateActorAndOperations("remove customers from Pit Tables");
        validateOpenBusinessDateAndLock();
        PitTableCustomerAssignment assignment = repository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Pit table customer assignment not found."));
        if (!tableId.equals(assignment.getPitTableId())) {
            throw new IllegalArgumentException("Assignment does not belong to the supplied Pit Table.");
        }
        if (assignment.getStatus() != PitTableCustomerAssignmentStatus.ACTIVE) {
            throw new IllegalArgumentException("Pit table customer assignment is not active.");
        }
        User actor = authenticatedUserService.getRequiredUser();
        assignment.setStatus(PitTableCustomerAssignmentStatus.LEFT);
        assignment.setLeftAt(LocalDateTime.now());
        assignment.setLeftBy(actor.getId());
        PitTableCustomerAssignment saved = repository.save(assignment);
        auditLogService.log("LEAVE_PIT_TABLE_CUSTOMER", "PIT_TABLE_CUSTOMER_ASSIGNMENT",
                saved.getId(), actor.getId(), "Customer left Pit Table " + tableId);
        return toResponse(saved, customerRepository.findById(saved.getCustomerId()).orElseThrow(),
                sessionRepository.findById(saved.getCustomerSessionId()).orElseThrow());
    }

    public List<PitTablePlayerResponse> getActivePlayers(UUID tableId) {
        tableRepository.findById(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Pit table not found."));
        return repository.findByPitTableIdAndStatusOrderByJoinedAtAsc(
                        tableId, PitTableCustomerAssignmentStatus.ACTIVE).stream()
                .map(value -> toResponse(value,
                        customerRepository.findById(value.getCustomerId()).orElseThrow(),
                        sessionRepository.findById(value.getCustomerSessionId()).orElseThrow()))
                .toList();
    }

    public List<PitTablePlayerResponse> getPlayerHistory(UUID tableId) {
        tableRepository.findById(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Pit table not found."));
        return repository.findByPitTableIdOrderByJoinedAtAsc(tableId).stream()
                .map(value -> toResponse(value,
                        customerRepository.findById(value.getCustomerId()).orElseThrow(),
                        sessionRepository.findById(value.getCustomerSessionId()).orElseThrow()))
                .toList();
    }

    private void validateActorAndOperations(String action) {
        if (!rolePermissionService.canPitTransaction(currentUserRoleService.getCurrentUserRole())) {
            throw new RuntimeException("Access denied. Only Dealer, Pit Supervisor or Super Admin can " + action + ".");
        }
    }

    private LocalDate validateOpenBusinessDateAndLock() {
        businessDateService.validateBusinessDateIsOpen();
        if (systemLockService.isSystemLocked()) {
            throw new RuntimeException("System is locked. Pit table player operations are not allowed.");
        }
        return businessDateService.getCurrentBusinessDate();
    }

    private Customer requiredActiveCustomer(UUID customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found."));
        if (customer.getStatus() != CustomerStatus.ACTIVE) {
            throw new IllegalArgumentException("Customer must be ACTIVE for Pit Table assignment.");
        }
        return customer;
    }

    private CustomerSession requiredOpenSession(UUID sessionId, UUID customerId, LocalDate businessDate) {
        CustomerSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer session not found."));
        if (!customerId.equals(session.getCustomerId())) {
            throw new IllegalArgumentException("Customer session does not belong to the supplied customer.");
        }
        if (!"OPEN".equalsIgnoreCase(session.getStatus())) {
            throw new IllegalArgumentException("Customer session must be OPEN.");
        }
        if (!businessDate.equals(session.getBusinessDate())) {
            throw new IllegalArgumentException("Customer session does not belong to the current OPEN Business Date.");
        }
        return session;
    }

    private PitTable requiredOpenTable(UUID tableId, LocalDate businessDate) {
        PitTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Pit table not found."));
        if (!"OPEN".equalsIgnoreCase(table.getStatus())) {
            throw new IllegalArgumentException("Pit table must be OPEN.");
        }
        if (!businessDate.equals(table.getBusinessDate())) {
            throw new IllegalArgumentException("Pit table does not belong to the current OPEN Business Date.");
        }
        return table;
    }

    private PitTablePlayerResponse toResponse(
            PitTableCustomerAssignment assignment, Customer customer, CustomerSession session) {
        return new PitTablePlayerResponse(assignment.getId(), assignment.getPitTableId(), customer.getId(),
                customer.getCustomerCode(), customer.getFullName(), session.getId(), session.getSessionCode(),
                null, assignment.getBusinessDate(), assignment.getJoinedAt(), assignment.getStatus(),
                assignment.getLeftAt());
    }
}
