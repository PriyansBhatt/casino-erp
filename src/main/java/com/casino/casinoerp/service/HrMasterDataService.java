package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.exception.*;
import com.casino.casinoerp.repository.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class HrMasterDataService {
    private final DepartmentRepository departments;
    private final JobTitleRepository jobTitles;
    private final AuthenticatedUserService authenticatedUsers;
    private final CurrentUserRoleService currentRoles;
    private final RolePermissionService permissions;
    private final AuditLogService audit;

    public HrMasterDataService(DepartmentRepository departments, JobTitleRepository jobTitles,
            AuthenticatedUserService authenticatedUsers, CurrentUserRoleService currentRoles,
            RolePermissionService permissions, AuditLogService audit) {
        this.departments = departments; this.jobTitles = jobTitles; this.authenticatedUsers = authenticatedUsers;
        this.currentRoles = currentRoles; this.permissions = permissions; this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<HrMasterDataResponse> departments() { requireManager(); return departments.findAllByOrderBySortOrderAscNameAsc().stream().map(this::response).toList(); }

    @Transactional(readOnly = true)
    public List<HrMasterDataResponse> jobTitles() { requireManager(); return jobTitles.findAllByOrderByNameAsc().stream().map(this::response).toList(); }

    @Transactional
    public HrMasterDataResponse createDepartment(HrMasterDataRequest request) {
        requireManager(); String code = canonical(request.code());
        if (departments.existsByCodeIgnoreCase(code)) throw new ResourceConflictException("Department code already exists.");
        Department value = new Department(); LocalDateTime now = LocalDateTime.now();
        value.setCode(code); value.setName(request.name().trim()); value.setDescription(normalize(request.description()));
        value.setActive(request.active() == null || request.active()); value.setSortOrder(request.sortOrder()); value.setCreatedAt(now); value.setUpdatedAt(now);
        try { value = departments.saveAndFlush(value); } catch (DataIntegrityViolationException ex) { throw new ResourceConflictException("Department code already exists."); }
        audit.log("CREATE_DEPARTMENT", "HR", value.getId(), authenticatedUsers.getRequiredUser().getId(), "Department code=" + value.getCode());
        return response(value);
    }

    @Transactional
    public HrMasterDataResponse updateDepartment(UUID id, HrMasterDataUpdateRequest request) {
        requireManager(); Department value = departments.findById(id).orElseThrow(() -> new ResourceNotFoundException("Department not found."));
        value.setName(request.name().trim()); value.setDescription(normalize(request.description())); value.setActive(request.active());
        value.setSortOrder(request.sortOrder()); value.setUpdatedAt(LocalDateTime.now()); value = departments.save(value);
        audit.log("UPDATE_DEPARTMENT", "HR", value.getId(), authenticatedUsers.getRequiredUser().getId(), "Department code=" + value.getCode()); return response(value);
    }

    @Transactional
    public HrMasterDataResponse createJobTitle(HrMasterDataRequest request) {
        requireManager(); String code = canonical(request.code());
        if (jobTitles.existsByCodeIgnoreCase(code)) throw new ResourceConflictException("Job Title code already exists.");
        JobTitle value = new JobTitle(); LocalDateTime now = LocalDateTime.now();
        value.setCode(code); value.setName(request.name().trim()); value.setDescription(normalize(request.description()));
        value.setActive(request.active() == null || request.active()); value.setCreatedAt(now); value.setUpdatedAt(now);
        try { value = jobTitles.saveAndFlush(value); } catch (DataIntegrityViolationException ex) { throw new ResourceConflictException("Job Title code already exists."); }
        audit.log("CREATE_JOB_TITLE", "HR", value.getId(), authenticatedUsers.getRequiredUser().getId(), "Job Title code=" + value.getCode()); return response(value);
    }

    @Transactional
    public HrMasterDataResponse updateJobTitle(UUID id, HrMasterDataUpdateRequest request) {
        requireManager(); JobTitle value = jobTitles.findById(id).orElseThrow(() -> new ResourceNotFoundException("Job Title not found."));
        value.setName(request.name().trim()); value.setDescription(normalize(request.description())); value.setActive(request.active()); value.setUpdatedAt(LocalDateTime.now()); value = jobTitles.save(value);
        audit.log("UPDATE_JOB_TITLE", "HR", value.getId(), authenticatedUsers.getRequiredUser().getId(), "Job Title code=" + value.getCode()); return response(value);
    }

    private void requireManager() {
        if (!currentRoles.getCurrentRole().map(permissions::canManageHr).orElse(false)) throw new org.springframework.security.access.AccessDeniedException("HR management is restricted to Director or Super Admin.");
    }
    private String canonical(String value) { return value.trim().toUpperCase(Locale.ROOT); }
    private String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private HrMasterDataResponse response(Department v) { return new HrMasterDataResponse(v.getId(),v.getCode(),v.getName(),v.getDescription(),v.isActive(),v.getSortOrder(),v.getCreatedAt(),v.getUpdatedAt()); }
    private HrMasterDataResponse response(JobTitle v) { return new HrMasterDataResponse(v.getId(),v.getCode(),v.getName(),v.getDescription(),v.isActive(),null,v.getCreatedAt(),v.getUpdatedAt()); }
}
