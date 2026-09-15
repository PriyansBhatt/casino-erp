package com.casino.casinoerp.service;
import com.casino.casinoerp.dto.CrmDtos.*;
import com.casino.casinoerp.repository.*;
import com.casino.casinoerp.security.Role;
import com.casino.casinoerp.exception.ResourceConflictException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
@Service
public class CrmService {
 private final CrmRepository repo; private final CustomerRepository customers; private final CustomerSessionRepository sessions;
 private final CurrentUserRoleService roles; private final AuthenticatedUserService users; private final BusinessDateRepository dates;
 private final BusinessDateService clock; private final SystemLockService locks; private final AuditLogService audit; private final ObjectMapper json;
 public CrmService(CrmRepository repo,CustomerRepository customers,CustomerSessionRepository sessions,CurrentUserRoleService roles,
 AuthenticatedUserService users,BusinessDateRepository dates,BusinessDateService clock,SystemLockService locks,AuditLogService audit,ObjectMapper json){
 this.repo=repo;this.customers=customers;this.sessions=sessions;this.roles=roles;this.users=users;this.dates=dates;this.clock=clock;this.locks=locks;this.audit=audit;this.json=json;}
 private void authorize(boolean mutation){var role=roles.getCurrentRole().orElse(null);if(role!=Role.SUPER_ADMIN&&(mutation||role!=Role.DIRECTOR))throw new AccessDeniedException("CRM requires SUPER_ADMIN; DIRECTOR is read-only.");}
 private LocalDate recordingDate(){var open=dates.findByStatus("OPEN");if(open.size()>1)throw new ResourceConflictException("Multiple OPEN Business Dates.");return open.isEmpty()?null:open.getFirst().getBusinessDate();}
 @Transactional(readOnly=true) public History history(String q,String type,String status,LocalDate from,LocalDate to){authorize(false);
 if(q.length()>100)throw new IllegalArgumentException("Search must not exceed 100 characters.");
 if(!type.isEmpty()&&!Set.of("SERVICE","HOTEL","TRANSPORT","GIFT","FOOD","TICKET","OTHER").contains(type))throw new IllegalArgumentException("Invalid record type.");
 if(status.length()>30||from!=null&&to!=null&&from.isAfter(to))throw new IllegalArgumentException("Invalid filters.");
 LocalDate date=recordingDate();return new History(date,date==null?"UNAVAILABLE":"OPEN",100,normalize(repo.history(q.isBlank()?"":"%"+q.trim().toLowerCase()+"%",type,status,from,to)));}
 private List<Map<String,Object>> normalize(List<Map<String,Object>> rows){return rows.stream().map(row->{Map<String,Object> result=new LinkedHashMap<>(row);result.replaceAll((k,v)->v instanceof java.sql.Timestamp t?t.toLocalDateTime().toString():v instanceof java.sql.Date d?d.toLocalDate().toString():v);return result;}).toList();}
 @Transactional(readOnly=true) public List<Map<String,Object>> customers(String q){authorize(false);if(q.length()>100)throw new IllegalArgumentException("Search too long.");return q.trim().length()<2?List.of():repo.customers(q.trim());}
 private void customer(UUID id,UUID session){if(customers.findById(id).isEmpty())throw new IllegalArgumentException("Customer not found.");
 // Optional link is historical context, not an assertion of a currently active casino visit.
 if(session!=null){var linked=sessions.findById(session).orElseThrow(()->new IllegalArgumentException("Session not found."));if(!id.equals(linked.getCustomerId()))throw new IllegalArgumentException("Session belongs to another customer.");}}
 private void unlocked(){dates.acquireLifecycleLock();if(locks.isSystemLocked())throw new ResourceConflictException("System is locked.");}
 private String signature(Object request){try{return json.writeValueAsString(request);}catch(Exception e){throw new IllegalArgumentException("Invalid CRM request.");}}
 @Transactional public Map<String,Object> create(ServiceCreate r){authorize(true);return create(false,r,r.customerId(),r.customerSessionId(),r.idempotencyKey());}
 @Transactional public Map<String,Object> create(TransportCreate r){authorize(true);return create(true,r,r.customerId(),r.customerSessionId(),r.idempotencyKey());}
 private Map<String,Object> create(boolean transport,Object request,UUID customer,UUID session,String key){
 UUID actor=users.getRequiredUser().getId();String signature=actor+":"+signature(request);repo.retryLock(key.trim());var replay=repo.replay(transport,key.trim());
 if(replay!=null){if(!signature.equals(replay.get("request_signature")))throw new ResourceConflictException("Retry key was used for a different record.");return Map.of("id",replay.get("id"),"replayed",true);}
 unlocked();customer(customer,session);LocalDate date=recordingDate();var now=clock.currentCasinoDateTime();
 UUID id=transport?repo.create((TransportCreate)request,actor,date,now,signature):repo.create((ServiceCreate)request,actor,date,now,signature);
 audit.log("CREATE_CRM_RECORD",transport?"CRM_TRANSPORT":"CRM_SERVICE",id,actor,"Informational record; no accounting posting");return Map.of("id",id,"replayed",false);}
 @Transactional public Map<String,Object> change(boolean transport,UUID id,Change r){authorize(true);unlocked();
 if(!(transport?Set.of("COMPLETED","CANCELLED"):Set.of("CANCELLED")).contains(r.status()))throw new IllegalArgumentException("Invalid CRM status transition.");
 if(repo.change(transport,id,r,clock.currentCasinoDateTime())!=1){if(repo.changedAlready(transport,id,r))return Map.of("id",id,"status",r.status(),"replayed",true);throw new ResourceConflictException("Record changed or transition is unavailable. Refresh first.");}
 audit.log("CHANGE_CRM_STATUS",transport?"CRM_TRANSPORT":"CRM_SERVICE",id,users.getRequiredUser().getId(),"status="+r.status());return Map.of("id",id,"status",r.status());}
}
