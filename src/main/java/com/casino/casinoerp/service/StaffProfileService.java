package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.exception.*;
import com.casino.casinoerp.repository.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class StaffProfileService {
    private final StaffProfileRepository profiles; private final UserRepository users;
    private final DepartmentRepository departments; private final JobTitleRepository jobTitles;
    private final AuthenticatedUserService authenticatedUsers; private final CurrentUserRoleService currentRoles;
    private final RolePermissionService permissions; private final AuditLogService audit;

    public StaffProfileService(StaffProfileRepository profiles, UserRepository users, DepartmentRepository departments,
            JobTitleRepository jobTitles, AuthenticatedUserService authenticatedUsers, CurrentUserRoleService currentRoles,
            RolePermissionService permissions, AuditLogService audit) {
        this.profiles=profiles; this.users=users; this.departments=departments; this.jobTitles=jobTitles;
        this.authenticatedUsers=authenticatedUsers; this.currentRoles=currentRoles; this.permissions=permissions; this.audit=audit;
    }

    @Transactional(readOnly=true) public List<StaffProfileResponse> list(){requireManager();return responses(profiles.findAllByOrderByEmployeeCodeAsc());}
    @Transactional(readOnly=true) public StaffProfileResponse get(UUID id){requireManager();return response(required(id));}
    @Transactional(readOnly=true) public StaffProfileResponse me(){User user=authenticatedUsers.getRequiredUser();return response(profiles.findByUserId(user.getId()).orElseThrow(()->new ResourceNotFoundException("Staff profile not found.")));}

    @Transactional
    public StaffProfileResponse create(CreateStaffProfileRequest request) {
        requireManager(); users.findById(request.userId()).orElseThrow(()->new ResourceNotFoundException("User not found."));
        if(profiles.existsByUserId(request.userId()))throw new ResourceConflictException("User already has a Staff Profile.");
        String employeeCode=canonical(request.employeeCode());
        if(profiles.existsByEmployeeCodeIgnoreCase(employeeCode))throw new ResourceConflictException("Employee code already exists.");
        Department department=activeDepartment(request.departmentId()); JobTitle title=activeJobTitle(request.jobTitleId());
        validateManager(null,request.reportingManagerStaffProfileId()); LocalDateTime now=LocalDateTime.now(); StaffProfile value=new StaffProfile();
        value.setUserId(request.userId()); value.setEmployeeCode(employeeCode); value.setDepartmentId(department.getId()); value.setJobTitleId(title.getId());
        apply(value,request.employmentStatus(),request.employmentType(),request.dateOfJoining(),request.phone(),request.reportingManagerStaffProfileId(),request.remarks());
        value.setCreatedAt(now); value.setUpdatedAt(now);
        try{value=profiles.saveAndFlush(value);}catch(DataIntegrityViolationException ex){throw new ResourceConflictException("Staff Profile conflicts with an existing user or employee code.");}
        audit.log("CREATE_STAFF_PROFILE","HR",value.getId(),authenticatedUsers.getRequiredUser().getId(),"Employee code="+value.getEmployeeCode()); return response(value);
    }

    @Transactional
    public StaffProfileResponse update(UUID id, UpdateStaffProfileRequest request) {
        requireManager(); StaffProfile value=required(id); activeDepartment(request.departmentId()); activeJobTitle(request.jobTitleId());
        validateManager(id,request.reportingManagerStaffProfileId()); value.setDepartmentId(request.departmentId()); value.setJobTitleId(request.jobTitleId());
        apply(value,request.employmentStatus(),request.employmentType(),request.dateOfJoining(),request.phone(),request.reportingManagerStaffProfileId(),request.remarks());
        value.setUpdatedAt(LocalDateTime.now()); value=profiles.save(value);
        audit.log("UPDATE_STAFF_PROFILE","HR",value.getId(),authenticatedUsers.getRequiredUser().getId(),"Employee code="+value.getEmployeeCode()); return response(value);
    }

    private void apply(StaffProfile v,EmploymentStatus status,EmploymentType type,java.time.LocalDate joining,String phone,UUID manager,String remarks){v.setEmploymentStatus(status);v.setEmploymentType(type);v.setDateOfJoining(joining);v.setPhone(normalize(phone));v.setReportingManagerStaffProfileId(manager);v.setRemarks(normalize(remarks));}
    private Department activeDepartment(UUID id){Department v=departments.findById(id).orElseThrow(()->new ResourceNotFoundException("Department not found."));if(!v.isActive())throw new IllegalArgumentException("Department must be ACTIVE.");return v;}
    private JobTitle activeJobTitle(UUID id){JobTitle v=jobTitles.findById(id).orElseThrow(()->new ResourceNotFoundException("Job Title not found."));if(!v.isActive())throw new IllegalArgumentException("Job Title must be ACTIVE.");return v;}
    private void validateManager(UUID self,UUID manager){if(manager==null)return;if(manager.equals(self))throw new IllegalArgumentException("A Staff Profile cannot report to itself.");profiles.findById(manager).orElseThrow(()->new ResourceNotFoundException("Reporting manager Staff Profile not found."));}
    private StaffProfile required(UUID id){return profiles.findById(id).orElseThrow(()->new ResourceNotFoundException("Staff profile not found."));}
    private void requireManager(){if(!currentRoles.getCurrentRole().map(permissions::canManageHr).orElse(false))throw new AccessDeniedException("HR management is restricted to Director or Super Admin.");}
    private String canonical(String v){return v.trim().toUpperCase(Locale.ROOT);} private String normalize(String v){return v==null||v.isBlank()?null:v.trim();}
    private List<StaffProfileResponse> responses(List<StaffProfile> values){return values.stream().map(this::response).toList();}
    private StaffProfileResponse response(StaffProfile v){
        User user=users.findById(v.getUserId()).orElse(null); Department department=departments.findById(v.getDepartmentId()).orElse(null); JobTitle title=jobTitles.findById(v.getJobTitleId()).orElse(null);
        StaffProfile manager=v.getReportingManagerStaffProfileId()==null?null:profiles.findById(v.getReportingManagerStaffProfileId()).orElse(null); User managerUser=manager==null?null:users.findById(manager.getUserId()).orElse(null);
        return new StaffProfileResponse(v.getId(),v.getUserId(),user==null?null:user.getUsername(),user==null?null:user.getFullName(),v.getEmployeeCode(),master(department),master(title),v.getEmploymentStatus(),v.getEmploymentType(),v.getDateOfJoining(),v.getPhone(),manager==null?null:new StaffProfileResponse.StaffManagerSummary(manager.getId(),manager.getEmployeeCode(),managerUser==null?null:managerUser.getUsername(),managerUser==null?null:managerUser.getFullName()),v.getRemarks(),v.getCreatedAt(),v.getUpdatedAt());
    }
    private HrMasterDataResponse master(Department v){return v==null?null:new HrMasterDataResponse(v.getId(),v.getCode(),v.getName(),v.getDescription(),v.isActive(),v.getSortOrder(),v.getCreatedAt(),v.getUpdatedAt());}
    private HrMasterDataResponse master(JobTitle v){return v==null?null:new HrMasterDataResponse(v.getId(),v.getCode(),v.getName(),v.getDescription(),v.isActive(),null,v.getCreatedAt(),v.getUpdatedAt());}
}
