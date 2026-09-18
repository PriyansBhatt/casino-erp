package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.AuditLogPage;
import com.casino.casinoerp.repository.AuditLogReadRepository;
import java.time.*;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogReadService {
    private final AuditLogReadRepository reads;
    private final CurrentUserRoleService roles;
    private final RolePermissionService permissions;
    public AuditLogReadService(AuditLogReadRepository reads, CurrentUserRoleService roles, RolePermissionService permissions) {
        this.reads=reads; this.roles=roles; this.permissions=permissions;
    }
    @Transactional(readOnly=true)
    public AuditLogPage read(int page, int size, LocalDateTime from, LocalDateTime to, LocalDate businessDate,
            String action, String module, UUID actorId, String search) {
        if (!permissions.canViewAuditLogs(roles.getCurrentUserRole())) throw new AccessDeniedException("Only Director or Super Admin may read audit logs.");
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Page must be non-negative and size must be between 1 and 100.");
        if (from != null && to != null && !from.isBefore(to)) throw new IllegalArgumentException("From must be before the exclusive To timestamp.");
        return reads.read(page,size,from,to,businessDate,text(action,100),text(module,100),actorId,text(search,200));
    }
    private String text(String value,int max) {
        if (value == null || value.isBlank()) return null;
        if (value.length()>max) throw new IllegalArgumentException("Audit filter is too long.");
        return value.trim();
    }
}
