package com.casino.casinoerp.service;

import com.casino.casinoerp.entity.AuditLog;
import com.casino.casinoerp.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
        AuditLog auditLog = new AuditLog();
        
        auditLog.setBusinessDate(businessDateService.getCurrentBusinessDate());
        auditLog.setActionType(actionType);
        auditLog.setModuleName(moduleName);
        auditLog.setEntityId(entityId);
        auditLog.setPerformedBy(performedBy);
        auditLog.setPerformedAt(LocalDateTime.now());
        auditLog.setRemarks(remarks);

        return auditLogRepository.save(auditLog);
    }

    public List<AuditLog> getByUser(UUID userId) {
        return auditLogRepository.findByPerformedBy(userId);
    }

    public List<AuditLog> getByAction(String action) {
        return auditLogRepository.findByActionType(action);
    }

    public List<AuditLog> getByModule(String module) {
        return auditLogRepository.findByModuleName(module);
    }

    public List<AuditLog> getAll() {
        String role = currentUserRoleService.getCurrentUserRole();

        if (!rolePermissionService.canViewAuditLogs(role)) {
            throw new RuntimeException(
                    "Access denied. Only Compliance Officer, Surveillance Officer or Super Admin can view audit logs."
            );
        }

        return auditLogRepository.findAll();
    }

    public List<AuditLog> getByBusinessDate(LocalDate businessDate) {
        String role = currentUserRoleService.getCurrentUserRole();

        if (!rolePermissionService.canViewAuditLogs(role)) {
            throw new RuntimeException(
                    "Access denied. Only Compliance Officer, Surveillance Officer or Super Admin can view audit logs."
            );
        }

        return auditLogRepository.findByBusinessDate(businessDate);
    }
}