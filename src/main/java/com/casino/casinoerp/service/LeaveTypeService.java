package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.entity.LeaveType;
import com.casino.casinoerp.exception.*;
import com.casino.casinoerp.repository.LeaveTypeRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class LeaveTypeService {
    private final LeaveTypeRepository leaveTypes;
    private final AuthenticatedUserService authenticatedUsers;
    private final CurrentUserRoleService currentRoles;
    private final RolePermissionService permissions;
    private final AuditLogService audit;

    public LeaveTypeService(LeaveTypeRepository leaveTypes,
            AuthenticatedUserService authenticatedUsers, CurrentUserRoleService currentRoles,
            RolePermissionService permissions, AuditLogService audit) {
        this.leaveTypes = leaveTypes;
        this.authenticatedUsers = authenticatedUsers;
        this.currentRoles = currentRoles;
        this.permissions = permissions;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<LeaveTypeResponse> list() {
        requireManager();
        return leaveTypes.findAllByOrderByNameAsc().stream().map(this::response).toList();
    }

    @Transactional
    public LeaveTypeResponse create(CreateLeaveTypeRequest request) {
        requireManager();
        String code = canonical(request.code());
        if (leaveTypes.existsByCodeIgnoreCase(code)) {
            throw new ResourceConflictException("Leave Type code already exists.");
        }
        LocalDateTime now = LocalDateTime.now();
        LeaveType value = new LeaveType();
        value.setCode(code);
        apply(value, request.name(), request.description(), request.active());
        value.setCreatedAt(now);
        value.setUpdatedAt(now);
        try {
            value = leaveTypes.saveAndFlush(value);
        } catch (DataIntegrityViolationException exception) {
            throw new ResourceConflictException("Leave Type code already exists.");
        }
        audit.log("CREATE_LEAVE_TYPE", "HR", value.getId(), authenticatedUsers.getRequiredUser().getId(),
                "Leave Type code=" + value.getCode());
        return response(value);
    }

    @Transactional
    public LeaveTypeResponse update(UUID id, UpdateLeaveTypeRequest request) {
        requireManager();
        LeaveType value = leaveTypes.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave Type not found."));
        apply(value, request.name(), request.description(), request.active());
        value.setUpdatedAt(LocalDateTime.now());
        value = leaveTypes.save(value);
        audit.log("UPDATE_LEAVE_TYPE", "HR", value.getId(), authenticatedUsers.getRequiredUser().getId(),
                "Leave Type code=" + value.getCode() + ", active=" + value.isActive());
        return response(value);
    }

    private void apply(LeaveType value, String name, String description, boolean active) {
        value.setName(name.trim());
        value.setDescription(normalize(description));
        value.setActive(active);
    }

    private LeaveTypeResponse response(LeaveType value) {
        return new LeaveTypeResponse(value.getId(), value.getCode(), value.getName(),
                value.getDescription(), value.isActive(), value.getCreatedAt(), value.getUpdatedAt());
    }

    private void requireManager() {
        if (!currentRoles.getCurrentRole().map(permissions::canManageHr).orElse(false)) {
            throw new AccessDeniedException("Leave Type management is restricted to Director or Super Admin.");
        }
    }

    private String canonical(String value) { return value.trim().toUpperCase(Locale.ROOT); }
    private String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
