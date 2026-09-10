package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.exception.*;
import com.casino.casinoerp.repository.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
public class StaffRosterService {
    private static final ZoneId CASINO_ZONE = ZoneId.of("Asia/Kathmandu");
    private final StaffRosterAssignmentRepository rosters;private final StaffProfileRepository profiles;
    private final ShiftDefinitionRepository shifts;private final StaffLeaveRequestRepository leaveRequests;private final UserRepository users;private final AuthenticatedUserService authenticatedUsers;
    private final CurrentUserRoleService currentRoles;private final RolePermissionService permissions;private final AuditLogService audit;
    public StaffRosterService(StaffRosterAssignmentRepository rosters,StaffProfileRepository profiles,ShiftDefinitionRepository shifts,
            StaffLeaveRequestRepository leaveRequests,UserRepository users,AuthenticatedUserService authenticatedUsers,CurrentUserRoleService currentRoles,RolePermissionService permissions,AuditLogService audit){this.rosters=rosters;this.profiles=profiles;this.shifts=shifts;this.leaveRequests=leaveRequests;this.users=users;this.authenticatedUsers=authenticatedUsers;this.currentRoles=currentRoles;this.permissions=permissions;this.audit=audit;}

    @Transactional(readOnly=true) public List<StaffRosterResponse> list(LocalDate from,LocalDate to,UUID staff,UUID department,UUID shift,RosterStatus status){requireManager();if(from!=null&&to!=null&&to.isBefore(from))throw new IllegalArgumentException("Roster end date cannot be before start date.");return rosters.search(from,to,staff,department,shift,status).stream().map(this::response).toList();}
    @Transactional(readOnly=true) public StaffRosterResponse get(UUID id){requireManager();return response(required(id));}
    @Transactional public StaffRosterResponse create(CreateStaffRosterRequest request){
        requireManager();StaffProfile staff=eligibleStaffForUpdate(request.staffProfileId());ShiftDefinition shift=activeShift(request.shiftDefinitionId());validateApprovedLeave(staff.getId(),request.rosterDate(),shift);validateNoOverlap(null,staff.getId(),request.rosterDate(),shift);
        StaffRosterAssignment value=new StaffRosterAssignment();LocalDateTime now=LocalDateTime.now();value.setStaffProfileId(staff.getId());value.setShiftDefinitionId(shift.getId());value.setRosterDate(request.rosterDate());value.setStatus(RosterStatus.SCHEDULED);value.setRemarks(normalize(request.remarks()));value.setCreatedAt(now);value.setUpdatedAt(now);
        try{value=rosters.saveAndFlush(value);}catch(DataIntegrityViolationException ex){throw new ResourceConflictException("Staff already has a scheduled roster assignment for this date.");}
        audit.log("CREATE_STAFF_ROSTER","HR",value.getId(),authenticatedUsers.getRequiredUser().getId(),reference(value,staff,shift));return response(value);
    }
    @Transactional public StaffRosterResponse update(UUID id,UpdateStaffRosterRequest request){
        requireManager();StaffRosterAssignment preview=required(id);StaffProfile staff=eligibleStaffForUpdate(preview.getStaffProfileId());StaffRosterAssignment value=rosters.findByIdForUpdate(id).orElseThrow(()->new ResourceNotFoundException("Staff roster assignment not found."));if(value.getStatus()!=RosterStatus.SCHEDULED)throw new ResourceConflictException("Only a SCHEDULED roster assignment can be updated.");ShiftDefinition shift=activeShift(request.shiftDefinitionId());validateApprovedLeave(staff.getId(),request.rosterDate(),shift);validateNoOverlap(id,staff.getId(),request.rosterDate(),shift);
        value.setShiftDefinitionId(shift.getId());value.setRosterDate(request.rosterDate());value.setRemarks(normalize(request.remarks()));value.setUpdatedAt(LocalDateTime.now());
        try{value=rosters.saveAndFlush(value);}catch(DataIntegrityViolationException ex){throw new ResourceConflictException("Staff already has a scheduled roster assignment for this date.");}
        audit.log("UPDATE_STAFF_ROSTER","HR",value.getId(),authenticatedUsers.getRequiredUser().getId(),reference(value,staff,shift));return response(value);
    }
    @Transactional public StaffRosterResponse cancel(UUID id,CancelStaffRosterRequest request){
        requireManager();StaffRosterAssignment value=required(id);if(value.getStatus()!=RosterStatus.SCHEDULED)throw new ResourceConflictException("Only a SCHEDULED roster assignment can be cancelled.");User actor=authenticatedUsers.getRequiredUser();LocalDateTime now=LocalDateTime.now();value.setStatus(RosterStatus.CANCELLED);value.setCancellationReason(request.reason().trim());value.setCancelledBy(actor.getId());value.setCancelledAt(now);value.setUpdatedAt(now);value=rosters.save(value);
        audit.log("CANCEL_STAFF_ROSTER","HR",value.getId(),actor.getId(),"Staff profile="+value.getStaffProfileId()+", rosterDate="+value.getRosterDate()+", reason="+value.getCancellationReason());return response(value);
    }
    private void validateNoOverlap(UUID excludedId,UUID staffId,LocalDate date,ShiftDefinition candidate){LocalDateTime start=start(date,candidate);LocalDateTime end=end(date,candidate);for(StaffRosterAssignment existing:rosters.findByStaffProfileIdAndStatusAndRosterDateBetweenOrderByRosterDateAsc(staffId,RosterStatus.SCHEDULED,date.minusDays(1),date.plusDays(1))){if(existing.getId().equals(excludedId))continue;ShiftDefinition existingShift=shifts.findById(existing.getShiftDefinitionId()).orElseThrow(()->new IllegalStateException("Roster references a missing Shift Definition."));LocalDateTime existingStart=start(existing.getRosterDate(),existingShift);LocalDateTime existingEnd=end(existing.getRosterDate(),existingShift);if(start.isBefore(existingEnd)&&existingStart.isBefore(end))throw new ResourceConflictException("Staff roster assignment overlaps an existing SCHEDULED assignment.");}}
    private void validateApprovedLeave(UUID staffId,LocalDate date,ShiftDefinition shift){ScheduledInterval candidate=interval(date,shift);LocalDate first=candidate.start().toLocalDate();LocalDate last=candidate.end().minusNanos(1).toLocalDate();for(StaffLeaveRequest leave:leaveRequests.findByStaffAndStatusOverlappingDates(staffId,LeaveRequestStatus.APPROVED,first,last)){if(leave.getStatus()!=LeaveRequestStatus.APPROVED)continue;ZonedDateTime leaveStart=leave.getStartDate().atStartOfDay(CASINO_ZONE);ZonedDateTime leaveEnd=leave.getEndDate().plusDays(1).atStartOfDay(CASINO_ZONE);if(candidate.start().isBefore(leaveEnd)&&leaveStart.isBefore(candidate.end()))throw new ResourceConflictException("Staff member has approved leave that conflicts with the selected roster shift.");}}
    private ScheduledInterval interval(LocalDate date,ShiftDefinition shift){return new ScheduledInterval(start(date,shift).atZone(CASINO_ZONE),end(date,shift).atZone(CASINO_ZONE));}
    private LocalDateTime start(LocalDate date,ShiftDefinition shift){return date.atTime(shift.getStartTime());}private LocalDateTime end(LocalDate date,ShiftDefinition shift){return (shift.isCrossesMidnight()?date.plusDays(1):date).atTime(shift.getEndTime());}
    private StaffProfile eligibleStaffForUpdate(UUID id){StaffProfile v=profiles.findByIdForUpdate(id).orElseThrow(()->new ResourceNotFoundException("Staff Profile not found."));if(v.getEmploymentStatus()!=EmploymentStatus.ACTIVE)throw new IllegalArgumentException("Only an ACTIVE Staff Profile can be scheduled.");return v;}
    private ShiftDefinition activeShift(UUID id){ShiftDefinition v=shifts.findById(id).orElseThrow(()->new ResourceNotFoundException("Shift Definition not found."));if(!v.isActive())throw new IllegalArgumentException("Shift Definition must be ACTIVE.");return v;}
    private StaffRosterAssignment required(UUID id){return rosters.findById(id).orElseThrow(()->new ResourceNotFoundException("Staff roster assignment not found."));}
    private void requireManager(){if(!currentRoles.getCurrentRole().map(permissions::canManageHr).orElse(false))throw new AccessDeniedException("HR management is restricted to Director or Super Admin.");}
    private String normalize(String v){return v==null||v.isBlank()?null:v.trim();}private String reference(StaffRosterAssignment v,StaffProfile s,ShiftDefinition shift){return "Employee code="+s.getEmployeeCode()+", shift="+shift.getCode()+", rosterDate="+v.getRosterDate();}
    private StaffRosterResponse response(StaffRosterAssignment v){StaffProfile staff=profiles.findById(v.getStaffProfileId()).orElse(null);ShiftDefinition shift=shifts.findById(v.getShiftDefinitionId()).orElse(null);User user=staff==null?null:users.findById(staff.getUserId()).orElse(null);return new StaffRosterResponse(v.getId(),staff==null?null:new StaffRosterResponse.StaffReference(staff.getId(),staff.getUserId(),staff.getEmployeeCode(),user==null?null:user.getUsername(),user==null?null:user.getFullName()),shift==null?null:new ShiftDefinitionResponse(shift.getId(),shift.getCode(),shift.getName(),shift.getDescription(),shift.getStartTime(),shift.getEndTime(),shift.isCrossesMidnight(),shift.getLateGraceMinutes(),shift.getEarlyCheckInMinutes(),shift.isActive(),shift.getCreatedAt(),shift.getUpdatedAt()),v.getRosterDate(),shift==null?null:start(v.getRosterDate(),shift),shift==null?null:end(v.getRosterDate(),shift),v.getStatus(),v.getRemarks(),v.getCancellationReason(),v.getCancelledBy(),v.getCancelledAt(),v.getCreatedAt(),v.getUpdatedAt());}
    private record ScheduledInterval(ZonedDateTime start,ZonedDateTime end){}
}
