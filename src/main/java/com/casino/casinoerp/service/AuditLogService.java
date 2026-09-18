package com.casino.casinoerp.service;

import com.casino.casinoerp.entity.AuditLog;
import com.casino.casinoerp.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final BusinessDateService businessDateService;
    private final RolePermissionService rolePermissionService;
    private final CurrentUserRoleService currentUserRoleService;

    public AuditLogService(
            AuditLogRepository auditLogRepository,
            BusinessDateService businessDateService,
            RolePermissionService rolePermissionService,
            CurrentUserRoleService currentUserRoleService) {

        this.auditLogRepository = auditLogRepository;
        this.businessDateService = businessDateService;
        this.rolePermissionService = rolePermissionService;
        this.currentUserRoleService = currentUserRoleService;
    }

    public AuditLog log(String actionType, String moduleName, UUID entityId, UUID performedBy, String remarks) {
        return logForBusinessDate(businessDateService.getCurrentBusinessDate(), actionType, moduleName, entityId, performedBy, remarks);
    }

    public AuditLog logForBusinessDate(LocalDate businessDate, String actionType, String moduleName,
            UUID entityId, UUID performedBy, String remarks) {
        AuditLog auditLog = new AuditLog();
        
        auditLog.setBusinessDate(businessDate);
        auditLog.setActionType(actionType);
        auditLog.setModuleName(moduleName);
        auditLog.setEntityId(entityId);
        auditLog.setPerformedBy(performedBy);
        auditLog.setPerformedAt(LocalDateTime.now());
        auditLog.setRemarks(remarks);

        return auditLogRepository.save(auditLog);
    }

    // Existing Business Date summary needs only a count, never hydrated audit history.
    public long countByBusinessDate(LocalDate date) {
        if (!rolePermissionService.canViewAuditLogs(currentUserRoleService.getCurrentUserRole()))
            throw new org.springframework.security.access.AccessDeniedException("Audit access denied.");
        return auditLogRepository.countByBusinessDate(date);
    }
}
