package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.exception.*;
import com.casino.casinoerp.repository.ShiftDefinitionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
public class ShiftDefinitionService {
    private final ShiftDefinitionRepository shifts; private final AuthenticatedUserService authenticatedUsers;
    private final CurrentUserRoleService currentRoles; private final RolePermissionService permissions; private final AuditLogService audit;
    public ShiftDefinitionService(ShiftDefinitionRepository shifts,AuthenticatedUserService authenticatedUsers,
            CurrentUserRoleService currentRoles,RolePermissionService permissions,AuditLogService audit){this.shifts=shifts;this.authenticatedUsers=authenticatedUsers;this.currentRoles=currentRoles;this.permissions=permissions;this.audit=audit;}

    @Transactional(readOnly=true) public List<ShiftDefinitionResponse> list(){requireManager();return shifts.findAllByOrderByNameAsc().stream().map(this::response).toList();}
    @Transactional(readOnly=true) public ShiftDefinitionResponse get(UUID id){requireManager();return response(required(id));}
    @Transactional public ShiftDefinitionResponse create(CreateShiftDefinitionRequest request){
        requireManager();String code=request.code().trim().toUpperCase(Locale.ROOT);if(shifts.existsByCodeIgnoreCase(code))throw new ResourceConflictException("Shift code already exists.");
        validateTimes(request.startTime(),request.endTime(),request.crossesMidnight());ShiftDefinition value=new ShiftDefinition();LocalDateTime now=LocalDateTime.now();
        value.setCode(code);apply(value,request.name(),request.description(),request.startTime(),request.endTime(),request.crossesMidnight(),request.lateGraceMinutes(),request.earlyCheckInMinutes(),request.active()==null||request.active());value.setCreatedAt(now);value.setUpdatedAt(now);
        try{value=shifts.saveAndFlush(value);}catch(DataIntegrityViolationException ex){throw new ResourceConflictException("Shift code already exists.");}
        audit.log("CREATE_SHIFT_DEFINITION","HR",value.getId(),authenticatedUsers.getRequiredUser().getId(),"Shift code="+value.getCode());return response(value);
    }
    @Transactional public ShiftDefinitionResponse update(UUID id,UpdateShiftDefinitionRequest request){
        requireManager();ShiftDefinition value=required(id);validateTimes(request.startTime(),request.endTime(),request.crossesMidnight());
        apply(value,request.name(),request.description(),request.startTime(),request.endTime(),request.crossesMidnight(),request.lateGraceMinutes(),request.earlyCheckInMinutes(),request.active());value.setUpdatedAt(LocalDateTime.now());value=shifts.save(value);
        audit.log("UPDATE_SHIFT_DEFINITION","HR",value.getId(),authenticatedUsers.getRequiredUser().getId(),"Shift code="+value.getCode());return response(value);
    }
    private void apply(ShiftDefinition v,String name,String description,LocalTime start,LocalTime end,boolean crosses,int late,int early,boolean active){if(late<0||early<0)throw new IllegalArgumentException("Shift grace minutes cannot be negative.");v.setName(name.trim());v.setDescription(normalize(description));v.setStartTime(start);v.setEndTime(end);v.setCrossesMidnight(crosses);v.setLateGraceMinutes(late);v.setEarlyCheckInMinutes(early);v.setActive(active);}
    private void validateTimes(LocalTime start,LocalTime end,boolean crosses){if(start.equals(end))throw new IllegalArgumentException("Shift start and end times must be different.");boolean expected=end.isBefore(start);if(crosses!=expected)throw new IllegalArgumentException("crossesMidnight is inconsistent with the shift start and end times.");}
    private ShiftDefinition required(UUID id){return shifts.findById(id).orElseThrow(()->new ResourceNotFoundException("Shift Definition not found."));}
    private void requireManager(){if(!currentRoles.getCurrentRole().map(permissions::canManageHr).orElse(false))throw new AccessDeniedException("HR management is restricted to Director or Super Admin.");}
    private String normalize(String v){return v==null||v.isBlank()?null:v.trim();}
    private ShiftDefinitionResponse response(ShiftDefinition v){return new ShiftDefinitionResponse(v.getId(),v.getCode(),v.getName(),v.getDescription(),v.getStartTime(),v.getEndTime(),v.isCrossesMidnight(),v.getLateGraceMinutes(),v.getEarlyCheckInMinutes(),v.isActive(),v.getCreatedAt(),v.getUpdatedAt());}
}
