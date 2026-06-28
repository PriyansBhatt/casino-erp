package com.casino.casinoerp.service;

import com.casino.casinoerp.entity.CustomerSession;
import com.casino.casinoerp.repository.CustomerSessionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomerSessionService {

    private final CustomerSessionRepository repository;
    private final BusinessDateService businessDateService;
    private final SystemLockService systemLockService;
    private final AuditLogService auditLogService;
    private final RolePermissionService rolePermissionService;
    private final CurrentUserRoleService currentUserRoleService;

    public CustomerSessionService(
            CustomerSessionRepository repository,
            BusinessDateService businessDateService,
            SystemLockService systemLockService,
            AuditLogService auditLogService,
            RolePermissionService rolePermissionService,
            CurrentUserRoleService currentUserRoleService) {

        this.repository = repository;
        this.businessDateService = businessDateService;
        this.systemLockService = systemLockService;
        this.auditLogService = auditLogService;
        this.rolePermissionService = rolePermissionService;
        this.currentUserRoleService = currentUserRoleService;
    }

    public List<CustomerSession> getAllSessions() {
        return repository.findAll();
    }

    public CustomerSession save(CustomerSession session) {

        String role = currentUserRoleService.getCurrentUserRole();

        if (!rolePermissionService.canCreateSession(role)) {
            throw new RuntimeException(
                    "Access denied. Only Receptionist or Super Admin can create sessions."
            );
        }

        businessDateService.validateBusinessDateIsOpen();

        if (systemLockService.isSystemLocked()) {
            throw new RuntimeException(
                    "System is locked. Customer sessions are not allowed."
            );
        }

        session.setBusinessDate(
                businessDateService.getCurrentBusinessDate()
        );

        CustomerSession saved = repository.save(session);

        auditLogService.log(
                "CREATE_SESSION",
                "CUSTOMER_SESSION",
                saved.getId(),
                currentUserRoleService.getCurrentUserId(),
                "Customer session created: " + saved.getSessionCode()
        );

        return saved;
    }
    
    public CustomerSession closeSession(java.util.UUID sessionId) {

        String role = currentUserRoleService.getCurrentUserRole();

        if (!rolePermissionService.canCreateSession(role)) {
            throw new RuntimeException(
                    "Access denied. Only Receptionist or Super Admin can close sessions."
            );
        }

        businessDateService.validateBusinessDateIsOpen();

        if (systemLockService.isSystemLocked()) {
            throw new RuntimeException(
                    "System is locked. Customer sessions cannot be closed."
            );
        }

        CustomerSession session = repository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Customer session not found."));

        if ("CLOSED".equalsIgnoreCase(session.getStatus())) {
            throw new RuntimeException("Customer session is already CLOSED.");
        }

        session.setStatus("CLOSED");
        session.setExitTime(java.time.LocalDateTime.now());

        CustomerSession saved = repository.save(session);

        auditLogService.log(
                "CLOSE_SESSION",
                "CUSTOMER_SESSION",
                saved.getId(),
                currentUserRoleService.getCurrentUserId(),
                "Customer session closed: " + saved.getSessionCode()
        );

        return saved;
    }

}