package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.StoreDtos.*;
import com.casino.casinoerp.repository.StoreRepository;
import com.casino.casinoerp.security.Role;
import com.casino.casinoerp.exception.ResourceConflictException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import static com.casino.casinoerp.repository.StoreRepository.params;

@Service
@Transactional
public class StoreService {
    private final StoreRepository repo;
    private final CurrentUserRoleService roles;
    private final AuthenticatedUserService users;
    private final BusinessDateService dates;
    private final AuditLogService audit;
    public StoreService(StoreRepository repo,CurrentUserRoleService roles,AuthenticatedUserService users,BusinessDateService dates,AuditLogService audit) {
        this.repo=repo;this.roles=roles;this.users=users;this.dates=dates;this.audit=audit;
    }
    private void authorize(boolean write) {
        var role=roles.getCurrentRole().orElse(null);
        if(role!=Role.SUPER_ADMIN && (write || role!=Role.DIRECTOR))throw new AccessDeniedException("Store requires SUPER_ADMIN; DIRECTOR is read-only.");
    }
    private UUID actor() { authorize(true);return users.getRequiredUser().getId(); }
    private LocalDate businessDate() { return dates.getCurrentOpenBusinessDate().map(d->d.getBusinessDate()).orElse(null); }
    private LocalDateTime now() { return dates.currentCasinoDateTime(); }
    private static String text(String value,int max,boolean required) {
        String s=value==null?"":value.trim();
        if(s.length()>max || required && s.isEmpty())throw new IllegalArgumentException("Invalid Store text length.");
        return s;
    }
    private static int quantity(Integer value) {
        if(value==null || value<1)throw new IllegalArgumentException("Quantity must be a positive integer.");return value;
    }
    private static long number(Map<String,Object> row,String key) { return ((Number)row.get(key)).longValue(); }
    private static UUID id(Map<String,Object> row,String key) { return (UUID)row.get(key); }
    private static void conflict(boolean invalid,String message) { if(invalid)throw new ResourceConflictException(message); }
    private static void version(Map<String,Object> row,Long expected) {
        if(expected==null || expected<0)throw new IllegalArgumentException("Expected version required.");
        conflict(number(row,"version")!=expected,"Store record changed. Refresh before continuing.");
    }
    private static void active(Map<String,Object> item) { conflict(!Boolean.TRUE.equals(item.get("active")),"Item is inactive."); }
    private static String reference(String prefix,UUID id) { return prefix+"-"+id.toString().toUpperCase(Locale.ROOT); }
    /** Length-delimited fields, sorted request lines, normalized text; independent of JSON property order. */
    private static String signature(Object... values) {
        try {
            var digest=MessageDigest.getInstance("SHA-256");
            for(Object value:values) {String s=String.valueOf(value);digest.update((s.length()+":"+s).getBytes(StandardCharsets.UTF_8));}
            return HexFormat.of().formatHex(digest.digest());
        } catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private Receipt replay(String key,String fingerprint) {
        repo.retryLock(key);var row=repo.replay(key);if(row==null)return null;
        conflict(!fingerprint.equals(row.get("request_fingerprint")),"Retry key belongs to another actor, target or payload.");
        return new Receipt(id(row,"id"),(String)row.get("reference"));
    }
    private void audit(String action,UUID id,UUID actor,LocalDate date) {
        audit.logForBusinessDate(date,action,"STORE",id,actor,"Quantity-only Store operation; no financial posting.");
    }
    private String filters(String q,int page,int size) {
        authorize(false);
        if(page<0 || size<1 || size>100 || (long)page*size>Integer.MAX_VALUE)throw new IllegalArgumentException("Invalid Store page/size.");
        return text(q,100,false);
    }
    private String status(String value,String... allowed) {
        String s=text(value,30,false);if(!s.isEmpty()&&!List.of(allowed).contains(s))throw new IllegalArgumentException("Invalid Store status.");return s;
    }
    @Transactional(readOnly=true) public Page<Item> items(String q,Boolean active,int page,int size) {return repo.items(filters(q,page,size),active,page,size);}
    @Transactional(readOnly=true) public Page<Staff> staff(String q,int page,int size) {return repo.staff(filters(q,page,size),page,size);}
    @Transactional(readOnly=true) public Page<Request> requests(String q,String status,int page,int size) {
        return repo.requests(filters(q,page,size),status(status,"PENDING","PARTIALLY_FULFILLED","FULFILLED","CANCELLED"),page,size);
    }
    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ) public Detail detail(UUID id) {authorize(false);return repo.detail(id);}
    @Transactional(readOnly=true) public Page<Procurement> procurements(String q,String status,int page,int size) {
        return repo.procurements(filters(q,page,size),status(status,"PENDING","ORDERED","RECEIVED","CANCELLED"),page,size);
    }
    @Transactional(readOnly=true) public Page<Movement> movements(String q,String type,UUID item,int page,int size) {
        return repo.movements(filters(q,page,size),status(type,"OPENING","ISSUE","RECEIPT","ADJUSTMENT_IN","ADJUSTMENT_OUT"),item,page,size);
    }
    public Receipt item(UUID target,ItemWrite r) {
        UUID actor=actor();String code=text(r.code(),50,true).toUpperCase(Locale.ROOT);
        if(!code.matches("[A-Z0-9_-]+") || r.unit()==null || r.active()==null)throw new IllegalArgumentException("Invalid Store item.");
        String name=text(r.name(),150,true),category=text(r.category(),100,true);
        // Serialize normalized code claims even for different items. Always code lock before item lock.
        repo.retryLock("code:"+code);
        var existing=repo.row("select id from casino.store_items where code=:code",Map.of("code",code));
        conflict(existing!=null&&!existing.get("id").equals(target),"Item code is already in use.");
        UUID id=target==null?UUID.randomUUID():target;LocalDateTime now=now();
        var args=params("id",id,"code",code,"name",name,"category",category,"unit",r.unit().name(),"active",r.active(),"actor",actor,"now",now);
        String action="STORE_ITEM_CREATED";
        if(target==null) {
            repo.update("""
                insert into casino.store_items(id,code,name,category,unit,active,created_at,created_by,updated_at,updated_by)
                values(:id,:code,:name,:category,:unit,:active,:now,:actor,:now,:actor)
                """,args);
        } else {
            var item=repo.item(id);version(item,r.expectedVersion());
            conflict(repo.hasHistory(id)&&(!code.equals(item.get("code"))||!r.unit().name().equals(item.get("unit"))),"Code and unit cannot change after request or movement history.");
            conflict(Boolean.TRUE.equals(item.get("active"))&&!r.active()&&repo.hasOutstandingWork(id),"Resolve outstanding requests and active procurement before inactivating this item.");
            action=r.active().equals(item.get("active"))?"STORE_ITEM_UPDATED":"STORE_ITEM_STATUS_CHANGED";
            repo.update("update casino.store_items set code=:code,name=:name,category=:category,unit=:unit,active=:active,updated_at=:now,updated_by=:actor,version=version+1 where id=:id",args);
        }
        audit(action,id,actor,businessDate());return new Receipt(id,code);
    }
    public Receipt createRequest(RequestCreate r) {
        UUID actor=actor();String key=text(r.idempotencyKey(),100,true),remarks=text(r.remarks(),1000,false);
        if(r.staffProfileId()==null || r.lines()==null || r.lines().isEmpty() || r.lines().size()>50)throw new IllegalArgumentException("Select staff and 1–50 item lines.");
        var lines=new TreeMap<UUID,Integer>();
        for(var line:r.lines())if(line==null||line.itemId()==null||lines.put(line.itemId(),quantity(line.quantity()))!=null)throw new IllegalArgumentException("Duplicate or invalid item line.");
        String fingerprint=signature("REQUEST",actor,r.staffProfileId(),r.requiredDate(),remarks,lines);
        var replay=replay(key,fingerprint);if(replay!=null)return replay;
        var staff=repo.required("""
            select s.department_id,s.employment_status,d.active,u.status as user_status from casino.staff_profiles s
            join casino.departments d on d.id=s.department_id join core.users u on u.id=s.user_id
            where s.id=:id for share of s,d,u
            """,Map.of("id",r.staffProfileId()));
        conflict(!"ACTIVE".equals(staff.get("employment_status"))||!Boolean.TRUE.equals(staff.get("active"))||!"ACTIVE".equals(staff.get("user_status")),"Requester and department must be active.");
        var items=repo.rows("select * from casino.store_items where id in (:ids) order by id for update",Map.of("ids",lines.keySet()));
        if(items.size()!=lines.size())throw new com.casino.casinoerp.exception.ResourceNotFoundException("Store item not found.");
        items.forEach(StoreService::active);
        UUID id=UUID.randomUUID();String ref=reference("SR",id);
        repo.update("""
            insert into casino.store_requests(id,reference,department_id,requester_staff_profile_id,recorded_by_user_id,created_at,required_date,remarks,status,creation_retry_key,request_fingerprint)
            values(:id,:ref,:department,:staff,:actor,:now,:required,:remarks,'PENDING',:key,:fingerprint)
            """,params("id",id,"ref",ref,"department",staff.get("department_id"),"staff",r.staffProfileId(),"actor",actor,"now",now(),"required",r.requiredDate(),"remarks",remarks,"key",key,"fingerprint",fingerprint));
        for(var line:lines.entrySet())repo.update("insert into casino.store_request_lines(id,request_id,item_id,requested_quantity) values(:id,:request,:item,:quantity)",params("id",UUID.randomUUID(),"request",id,"item",line.getKey(),"quantity",line.getValue()));
        audit("STORE_REQUEST_CREATED",id,actor,businessDate());return new Receipt(id,ref);
    }
    // Every line/procurement operation locks its parent request first, then item. Cancellation uses the same header lock.
    private Map<String,Object> lockLine(UUID lineId) {
        var line=repo.line(lineId);repo.request(id(line,"request_id"));return repo.line(lineId);
    }
    private long outstanding(Map<String,Object> line) {return number(line,"requested_quantity")-number(line,"issued_quantity")-number(line,"cancelled_quantity");}
    public Receipt procure(UUID lineId,Quantity r) {
        UUID actor=actor();int qty=quantity(r.quantity());String key=text(r.idempotencyKey(),100,true),external=text(r.externalReference(),200,false);
        String fingerprint=signature("PROCURE",actor,lineId,qty,external);var replay=replay(key,fingerprint);if(replay!=null)return replay;
        var line=lockLine(lineId);active(repo.item(id(line,"item_id")));
        conflict(qty>outstanding(line),"Procurement exceeds request remainder.");
        conflict(repo.row("select id from casino.store_procurements where request_line_id=:id and status in ('PENDING','ORDERED')",Map.of("id",lineId))!=null,"An active procurement already exists for this line.");
        UUID id=UUID.randomUUID();String ref=reference("SP",id);
        repo.update("""
            insert into casino.store_procurements(id,reference,request_line_id,item_id,quantity,status,supplier_reference,created_by,created_at,creation_retry_key,request_fingerprint)
            values(:id,:ref,:line,:item,:quantity,'PENDING',:external,:actor,:now,:key,:fingerprint)
            """,params("id",id,"ref",ref,"line",lineId,"item",line.get("item_id"),"quantity",qty,"external",external,"actor",actor,"now",now(),"key",key,"fingerprint",fingerprint));
        audit("STORE_PROCUREMENT_CREATED",id,actor,businessDate());return new Receipt(id,ref);
    }
    public Receipt transition(UUID id,Transition r,boolean order) {
        UUID actor=actor();var p=repo.procurement(id);lockLine(id(p,"request_line_id"));p=repo.procurement(id);version(p,r.expectedVersion());
        String status=(String)p.get("status"),reason=text(r.reason(),500,!order),supplier=text(r.supplierReference(),200,false);
        conflict(order?!status.equals("PENDING"):!List.of("PENDING","ORDERED").contains(status),"Procurement transition unavailable.");
        var args=params("id",id,"actor",actor,"now",now(),"reason",reason,"supplier",supplier);
        repo.update(order?"update casino.store_procurements set status='ORDERED',ordered_at=:now,ordered_by=:actor,supplier_reference=:supplier,version=version+1 where id=:id":
            "update casino.store_procurements set status='CANCELLED',cancelled_at=:now,cancelled_by=:actor,cancellation_reason=:reason,version=version+1 where id=:id",args);
        audit(order?"STORE_PROCUREMENT_ORDERED":"STORE_PROCUREMENT_CANCELLED",id,actor,businessDate());return new Receipt(id,(String)p.get("reference"));
    }
    public Receipt cancel(UUID id,Transition r) {
        UUID actor=actor();var request=repo.request(id);version(request,r.expectedVersion());String reason=text(r.reason(),500,true);
        conflict(List.of("FULFILLED","CANCELLED").contains(request.get("status")),"Request has no remaining quantity.");
        conflict(repo.row("""
            select p.id from casino.store_procurements p join casino.store_request_lines l on l.id=p.request_line_id
            where l.request_id=:id and l.requested_quantity>l.issued_quantity+l.cancelled_quantity and p.status in ('PENDING','ORDERED') limit 1
            """,Map.of("id",id))!=null,"Cancel active procurement before cancelling the request remainder.");
        repo.update("update casino.store_request_lines set cancelled_quantity=requested_quantity-issued_quantity where request_id=:id",Map.of("id",id));
        repo.update("update casino.store_requests set cancelled_at=:now,cancelled_by=:actor,cancellation_reason=:reason where id=:id",params("id",id,"now",now(),"actor",actor,"reason",reason));
        deriveStatus(id);audit("STORE_REQUEST_CANCELLED",id,actor,businessDate());return new Receipt(id,(String)request.get("reference"));
    }
    private void deriveStatus(UUID requestId) {
        repo.update("""
            update casino.store_requests r set status=(select case
              when bool_and(issued_quantity=requested_quantity) then 'FULFILLED'
              when sum(requested_quantity::bigint-issued_quantity-cancelled_quantity)=0 then 'CANCELLED'
              when sum(issued_quantity)>0 then 'PARTIALLY_FULFILLED' else 'PENDING' end
              from casino.store_request_lines where request_id=r.id), version=version+1 where r.id=:id
            """,Map.of("id",requestId));
    }
    public Receipt opening(UUID item,Quantity r) {return movement(MovementType.OPENING,item,r.quantity(),r.idempotencyKey(),"",r.externalReference());}
    public Receipt issue(UUID line,Quantity r) {return movement(MovementType.ISSUE,line,r.quantity(),r.idempotencyKey(),"",r.externalReference());}
    public Receipt receive(UUID procurement,Quantity r) {return movement(MovementType.RECEIPT,procurement,r.quantity(),r.idempotencyKey(),"",r.externalReference());}
    public Receipt adjust(UUID item,Adjustment r) {
        authorize(true);if(r.type()!=MovementType.ADJUSTMENT_IN&&r.type()!=MovementType.ADJUSTMENT_OUT)throw new IllegalArgumentException("Invalid adjustment direction.");
        return movement(r.type(),item,r.quantity(),r.idempotencyKey(),text(r.reason(),500,true),"");
    }
    private Receipt movement(MovementType type,UUID target,Integer requested,String retry,String reason,String external) {
        UUID actor=actor();int qty=quantity(requested);String key=text(retry,100,true);external=text(external,200,false);
        String fingerprint=signature(type,actor,target,qty,reason,external);var replay=replay(key,fingerprint);if(replay!=null)return replay;
        UUID itemId=target,lineId=null,procurementId=null;Map<String,Object> line=null,p=null;
        if(type==MovementType.ISSUE) {lineId=target;line=lockLine(target);itemId=id(line,"item_id");conflict(qty>outstanding(line),"Issue exceeds request remainder.");}
        if(type==MovementType.RECEIPT) {
            procurementId=target;p=repo.procurement(target);lockLine(id(p,"request_line_id"));p=repo.procurement(target);itemId=id(p,"item_id");
            conflict(!"ORDERED".equals(p.get("status"))||qty>number(p,"quantity")-number(p,"received_quantity"),"Receipt exceeds ordered remainder or procurement is not ORDERED.");
        }
        var item=repo.item(itemId);active(item);
        long balance=number(item,"quantity_balance");
        if(type==MovementType.OPENING)conflict(balance!=0||repo.hasMovements(itemId),"Opening stock is allowed only before any movement, at zero balance.");
        long after=balance+(type==MovementType.ISSUE||type==MovementType.ADJUSTMENT_OUT?-qty:qty);
        conflict(after<0||after>Integer.MAX_VALUE,"Insufficient stock or quantity capacity exceeded.");
        LocalDate date=businessDate();LocalDateTime now=now();UUID id=UUID.randomUUID();String ref=reference("SM",id);
        repo.update("update casino.store_items set quantity_balance=:balance,version=version+1,updated_at=:now,updated_by=:actor where id=:id",params("id",itemId,"balance",after,"now",now,"actor",actor));
        repo.update("""
            insert into casino.store_movements(id,reference,item_id,movement_type,quantity,balance_after,request_line_id,procurement_id,performed_by,performed_at,business_date,reason,external_reference,idempotency_key,request_fingerprint)
            values(:id,:ref,:item,:type,:quantity,:balance,:line,:procurement,:actor,:now,:date,:reason,:external,:key,:fingerprint)
            """,params("id",id,"ref",ref,"item",itemId,"type",type.name(),"quantity",qty,"balance",after,"line",lineId,"procurement",procurementId,"actor",actor,"now",now,"date",date,"reason",reason,"external",external,"key",key,"fingerprint",fingerprint));
        if(line!=null) {
            repo.update("update casino.store_request_lines set issued_quantity=issued_quantity+:quantity where id=:id",params("id",lineId,"quantity",qty));deriveStatus(id(line,"request_id"));
        }
        if(p!=null)repo.update("""
            update casino.store_procurements set received_quantity=received_quantity+:quantity,
            status=case when received_quantity+:quantity=quantity then 'RECEIVED' else 'ORDERED' end,version=version+1 where id=:id
            """,params("id",procurementId,"quantity",qty));
        String action=switch(type) {case OPENING->"STORE_STOCK_OPENING";case ISSUE->"STORE_STOCK_ISSUED";case RECEIPT->"STORE_GOODS_RECEIVED";default->"STORE_STOCK_ADJUSTED";};
        audit(action,id,actor,date);return new Receipt(id,ref);
    }
}
