package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.FnbDtos.*;
import com.casino.casinoerp.entity.CustomerStatus;
import com.casino.casinoerp.repository.*;
import com.casino.casinoerp.security.Role;
import com.casino.casinoerp.exception.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
public class FnbService {
    private final FnbRepository repo;
    private final CustomerRepository customers;
    private final CustomerSessionRepository sessions;
    private final BusinessDateService dates;
    private final SystemLockService locks;
    private final CurrentUserRoleService roles;
    private final AuthenticatedUserService users;
    private final AuditLogService audit;
    private final ObjectMapper json;
    public FnbService(FnbRepository repo,CustomerRepository customers,CustomerSessionRepository sessions,
            BusinessDateService dates,SystemLockService locks,CurrentUserRoleService roles,
            AuthenticatedUserService users,AuditLogService audit,ObjectMapper json) {
        this.repo=repo;this.customers=customers;this.sessions=sessions;this.dates=dates;
        this.locks=locks;this.roles=roles;this.users=users;this.audit=audit;this.json=json;
    }
    private void authorize(boolean mutate) {
        Role role=roles.getCurrentRole().orElse(null);
        if(role!=Role.SUPER_ADMIN && (mutate || role!=Role.DIRECTOR))
            throw new AccessDeniedException("F&B requires SUPER_ADMIN; DIRECTOR is read-only.");
    }
    private LocalDate current() { return dates.getCurrentOpenBusinessDate().map(v->v.getBusinessDate()).orElse(null); }
    private void unlocked() { if(locks.isSystemLocked()) throw new ResourceConflictException("System is locked."); }
    @Transactional(readOnly=true)
    public Page history(LocalDate date,LocalDate from,LocalDate to,String type,String status,String q,boolean live) {
        authorize(false);
        if(q.length()>100 || from!=null && to!=null && from.isAfter(to))throw new IllegalArgumentException("Invalid F&B filters.");
        if(!type.isEmpty())Type.valueOf(type);
        if(!status.isEmpty())Status.valueOf(status);
        LocalDate open=current();
        return new Page(open,open==null?"UNAVAILABLE":"OPEN",100,repo.history(date,from,to,type,status,q,live));
    }
    @Transactional(readOnly=true) public Overview overview() {
        authorize(false);LocalDate date=current();
        return new Overview(date,date==null?"UNAVAILABLE":"OPEN",date!=null,date==null?null:repo.overview(date));
    }
    @Transactional(readOnly=true) public List<Map<String,Object>> customers(String q) {
        authorize(false);if(q.length()>100)throw new IllegalArgumentException("Search too long.");
        return q.trim().length()<2?List.of():repo.customers(q.trim());
    }
    @Transactional public Map<String,Object> create(Create r) {
        authorize(true);UUID actor=users.getRequiredUser().getId();String signature;
        try { signature=actor+":"+json.writeValueAsString(r); } catch(Exception e) { throw new IllegalArgumentException("Invalid F&B request."); }
        repo.retryLock(r.idempotencyKey().trim());var replay=repo.replay(r.idempotencyKey().trim());
        if(replay!=null) {
            if(!signature.equals(replay.get("request_signature")))throw new ResourceConflictException("Retry key belongs to a different request or actor.");
            return Map.of("id",replay.get("id"),"replayed",true);
        }
        dates.validateNewOperationalMutationAllowed();unlocked();LocalDate date=current();
        if(date==null || !date.equals(r.expectedBusinessDate()))throw new ResourceConflictException("Business Date changed or is unavailable. Refresh before creating a request.");
        if(r.quantity()==null || r.quantity()<1)throw new IllegalArgumentException("Quantity must be positive.");
        var customer=customers.findById(r.customerId()).orElseThrow(()->new IllegalArgumentException("Customer not found."));
        if(customer.getStatus()!=CustomerStatus.ACTIVE)throw new IllegalArgumentException("Customer must be ACTIVE.");
        // Optional Reception link is immutable historical context, not a new session opening.
        if(r.customerSessionId()!=null) {
            var session=sessions.findById(r.customerSessionId()).orElseThrow(()->new IllegalArgumentException("Session not found."));
            if(!r.customerId().equals(session.getCustomerId()))throw new IllegalArgumentException("Session belongs to another customer.");
        }
        UUID id=repo.create(r,actor,date,dates.currentCasinoDateTime(),signature);
        audit.log("CREATE_FNB_REQUEST","FNB_REQUEST",id,actor,"Complimentary operational quantity; no financial posting");
        return Map.of("id",id,"businessDate",date,"replayed",false);
    }
    public static boolean allowed(Status previous,Status next) {
        return switch(previous) {
            case PENDING -> next==Status.PREPARING || next==Status.CANCELLED;
            case PREPARING -> next==Status.READY || next==Status.CANCELLED;
            case READY -> next==Status.DELIVERED || next==Status.CANCELLED;
            case DELIVERED,CANCELLED -> false;
        };
    }
    @Transactional public Map<String,Object> change(UUID id,Change r) {
        authorize(true);UUID actor=users.getRequiredUser().getId();var row=repo.lock(id);
        if(row==null)throw new ResourceNotFoundException("F&B request not found.");
        if(repo.replayChange(id,r,actor))return Map.of("id",id,"replayed",true);
        dates.validateSettlementMutationAllowed();unlocked();
        if(((Number)row.get("version")).intValue()!=r.expectedVersion() || !allowed(Status.valueOf((String)row.get("status")),r.status()))
            throw new ResourceConflictException("F&B request changed or transition is unavailable. Refresh first.");
        repo.change(id,r,actor,dates.currentCasinoDateTime(),current());
        audit.log("CHANGE_FNB_STATUS","FNB_REQUEST",id,actor,"status="+r.status());
        return Map.of("id",id,"status",r.status(),"replayed",false);
    }
}
