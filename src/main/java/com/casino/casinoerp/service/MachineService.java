package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.MachineDtos.*;
import com.casino.casinoerp.entity.CustomerStatus;
import com.casino.casinoerp.exception.ResourceConflictException;
import com.casino.casinoerp.exception.ResourceNotFoundException;
import com.casino.casinoerp.repository.*;
import com.casino.casinoerp.security.Role;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;

@Service
public class MachineService {
    private final MachineRepository machines;
    private final BusinessDateRepository dateRepository;
    private final BusinessDateService dates;
    private final SystemLockService locks;
    private final CustomerRepository customers;
    private final CustomerSessionRepository sessions;
    private final CurrentUserRoleService roles;
    private final AuthenticatedUserService users;
    private final AuditLogService audit;
    public MachineService(MachineRepository machines, BusinessDateRepository dateRepository, BusinessDateService dates,
            SystemLockService locks, CustomerRepository customers, CustomerSessionRepository sessions,
            CurrentUserRoleService roles, AuthenticatedUserService users, AuditLogService audit) {
        this.machines=machines;this.dateRepository=dateRepository;this.dates=dates;this.locks=locks;
        this.customers=customers;this.sessions=sessions;this.roles=roles;this.users=users;this.audit=audit;
    }
    private void authorize(boolean mutation) {
        Role role=roles.getCurrentRole().orElse(null);
        if(role!=Role.SUPER_ADMIN && (mutation || role!=Role.DIRECTOR)) throw new AccessDeniedException("Machine management requires SUPER_ADMIN; DIRECTOR has read-only access.");
    }
    @Transactional(readOnly=true)
    public Overview overview() {
        authorize(false);
        var open=dateRepository.findByStatus("OPEN");
        if(open.size()>1) throw new ResourceConflictException("Multiple OPEN Business Dates.");
        return new Overview(open.isEmpty()?null:open.getFirst().getBusinessDate(),open.isEmpty()?"UNAVAILABLE":"OPEN",machines.overview());
    }
    @Transactional(readOnly=true)
    public Detail detail(UUID id) {
        authorize(false);return new Detail(required(id),machines.history(id),50);
    }
    @Transactional(readOnly=true)
    public List<Candidate> candidates(String query) {
        authorize(false);dates.validateBusinessDateIsOpen();
        String text=query==null?"":query.trim();
        if(text.length()<2) return List.of();
        if(text.length()>100) throw new IllegalArgumentException("Search must not exceed 100 characters.");
        return machines.candidates(text,dates.getCurrentBusinessDate());
    }
    @Transactional
    public Machine create(Create request) {
        authorize(true);operational(request.expectedBusinessDate(),false);
        UUID id=UUID.randomUUID(),actor=users.getRequiredUser().getId();
        try { machines.create(id,request,actor,dates.currentCasinoDateTime()); }
        catch(DataIntegrityViolationException ex) { throw new ResourceConflictException("Machine code already exists or machine data is invalid."); }
        audit.log("CREATE_MACHINE","GAMING_MACHINE",id,actor,"Created machine "+request.machineCode());
        return required(id);
    }
    @Transactional
    public Machine status(UUID id, ChangeStatus request) {
        authorize(true);operational(request.expectedBusinessDate(),false);
        var machine=machines.lock(id).orElseThrow(()->new ResourceNotFoundException("Machine not found."));
        if(machines.occupied(id,id)) throw new ResourceConflictException("End active play before changing machine availability.");
        if(!machine.availability().equals(request.expectedStatus().name())) throw new ResourceConflictException("Machine status changed. Refresh before continuing.");
        if(request.status()==request.expectedStatus()) throw new IllegalArgumentException("Choose a different availability state.");
        machines.status(id,request.status().name(),dates.currentCasinoDateTime());
        audit.log("CHANGE_MACHINE_STATUS","GAMING_MACHINE",id,users.getRequiredUser().getId(),machine.availability()+" -> "+request.status());
        return required(id);
    }
    @Transactional
    public Receipt start(UUID id, Start request) {
        authorize(true);dateRepository.acquireLifecycleLock();
        UUID actor=users.getRequiredUser().getId();
        var replay=machines.byKey(request.idempotencyKey().trim(),false).orElse(null);
        if(replay!=null) {
            if(!replay.machineId().equals(id)||!replay.customerId().equals(request.customerId())||!replay.sessionId().equals(request.customerSessionId())||!replay.date().equals(request.expectedBusinessDate())||!replay.startedBy().equals(actor)) throw new ResourceConflictException("Start key belongs to a different request.");
            return new Receipt(id,replay.id(),replay.date());
        }
        LocalDate date=operational(request.expectedBusinessDate(),false);
        machines.lockCustomer(request.customerId());
        var customer=customers.findById(request.customerId()).orElseThrow(()->new ResourceNotFoundException("Customer not found."));
        var session=sessions.findByIdForUpdate(request.customerSessionId()).orElseThrow(()->new ResourceNotFoundException("Customer session not found."));
        if(customer.getStatus()!=CustomerStatus.ACTIVE) throw new ResourceConflictException("Customer must be ACTIVE.");
        if(!customer.getId().equals(session.getCustomerId())) throw new IllegalArgumentException("Session does not belong to this customer.");
        if(!"OPEN".equalsIgnoreCase(session.getStatus())||session.getExitTime()!=null) throw new ResourceConflictException("Customer session must be OPEN and unexited.");
        if(!date.equals(session.getBusinessDate())) throw new ResourceConflictException("Customer session Business Date does not match the current OPEN date.");
        var machine=machines.lock(id).orElseThrow(()->new ResourceNotFoundException("Machine not found."));
        if(!"SLOT".equals(machine.type())) throw new ResourceConflictException("Automatic Roulette play workflow is not configured.");
        if(!"AVAILABLE".equals(machine.availability())||machines.occupied(id,session.getId())) throw new ResourceConflictException("Machine or customer session already occupied, or machine unavailable.");
        UUID play=UUID.randomUUID();
        try { machines.start(play,id,request,actor,date,dates.currentCasinoDateTime()); }
        catch(DataIntegrityViolationException ex) { throw new ResourceConflictException("Machine/session was assigned concurrently or request key is already used."); }
        audit.log("START_SLOT_PLAY","SLOT_PLAY",play,actor,"Started on machine "+id);
        return new Receipt(id,play,date);
    }
    @Transactional
    public Receipt end(UUID id, UUID playId, End request) {
        authorize(true);dateRepository.acquireLifecycleLock();
        UUID actor=users.getRequiredUser().getId();
        var replay=machines.byKey(request.idempotencyKey().trim(),true).orElse(null);
        if(replay!=null) {
            if(!replay.id().equals(playId)||!replay.machineId().equals(id)||!replay.date().equals(request.expectedBusinessDate())||!actor.equals(replay.endedBy())) throw new ResourceConflictException("End key belongs to a different request.");
            return new Receipt(id,playId,replay.date());
        }
        dates.validateSettlementMutationAllowed();
        dates.validateBusinessDateIsOpen();
        if(locks.isSystemLocked()) throw new ResourceConflictException("System is locked. Machine mutations are unavailable.");
        // Termination may follow rollover; expectedBusinessDate identifies the original assignment.
        machines.lock(id).orElseThrow(()->new ResourceNotFoundException("Machine not found."));
        var play=machines.play(playId).orElseThrow(()->new ResourceNotFoundException("Play not found."));
        if(!play.machineId().equals(id)||!play.date().equals(request.expectedBusinessDate())||!"ACTIVE".equals(play.status())) throw new ResourceConflictException("Play must be active on this machine and match its original Business Date.");
        try { machines.end(play,request,actor,dates.currentCasinoDateTime()); }
        catch(DataIntegrityViolationException ex) { throw new ResourceConflictException("End request key is already used."); }
        // Ending play changes only machine occupancy. It never exits the casino or moves money/chips.
        audit.log("END_SLOT_PLAY","SLOT_PLAY",playId,actor,"Ended play; no financial or casino-session mutation.");
        return new Receipt(id,playId,play.date());
    }
    private LocalDate operational(LocalDate expected, boolean settlement) {
        if(settlement) dates.validateSettlementMutationAllowed(); else dates.validateNewOperationalMutationAllowed();
        dates.validateBusinessDateIsOpen();
        if(locks.isSystemLocked()) throw new ResourceConflictException("System is locked. Machine mutations are unavailable.");
        LocalDate current=dates.getCurrentBusinessDate();
        if(!current.equals(expected)) throw new ResourceConflictException("Business Date changed. Refresh and review the operation.");
        return current;
    }
    private Machine required(UUID id) { return machines.detail(id).orElseThrow(()->new ResourceNotFoundException("Machine not found.")); }
}
