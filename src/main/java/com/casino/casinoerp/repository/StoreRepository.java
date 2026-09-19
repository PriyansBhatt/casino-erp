package com.casino.casinoerp.repository;

import com.casino.casinoerp.dto.StoreDtos.*;
import com.casino.casinoerp.exception.ResourceNotFoundException;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;

/** Bounded projections for reads; mutable maps below are private persistence rows, never API DTOs. */
@Repository
public class StoreRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public StoreRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
    public static Map<String,Object> params(Object... pairs) {
        var result = new HashMap<String,Object>();
        for (int i=0;i<pairs.length;i+=2) result.put((String)pairs[i], pairs[i+1]);
        return result;
    }
    public int update(String sql, Map<String,?> args) { return jdbc.update(sql,args); }
    public List<Map<String,Object>> rows(String sql, Map<String,?> args) { return jdbc.queryForList(sql,args); }
    public Map<String,Object> row(String sql, Map<String,?> args) {
        var rows=rows(sql,args); return rows.isEmpty()?null:rows.getFirst();
    }
    public Map<String,Object> required(String sql, Map<String,?> args) {
        var row=row(sql,args); if(row==null) throw new ResourceNotFoundException("Store record not found."); return row;
    }
    public void retryLock(String key) {
        jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(:key, 36001))",Map.of("key",key));
    }
    public Map<String,Object> replay(String key) {
        return row("""
            select id,reference,request_fingerprint from casino.store_requests where creation_retry_key=:key
            union all select id,reference,request_fingerprint from casino.store_procurements where creation_retry_key=:key
            union all select id,reference,request_fingerprint from casino.store_movements where idempotency_key=:key
            """,Map.of("key",key));
    }
    public Map<String,Object> item(UUID id) { return required("select * from casino.store_items where id=:id for update",Map.of("id",id)); }
    public Map<String,Object> request(UUID id) { return required("select * from casino.store_requests where id=:id for update",Map.of("id",id)); }
    public Map<String,Object> line(UUID id) { return required("select * from casino.store_request_lines where id=:id",Map.of("id",id)); }
    public Map<String,Object> procurement(UUID id) { return required("select * from casino.store_procurements where id=:id",Map.of("id",id)); }
    public boolean hasMovements(UUID id) { return (Boolean)required("select exists(select 1 from casino.store_movements where item_id=:id) as present",Map.of("id",id)).get("present"); }
    public boolean hasOutstandingWork(UUID id) { return (Boolean)required("select exists(select 1 from casino.store_request_lines where item_id=:id and requested_quantity-issued_quantity-cancelled_quantity>0) or exists(select 1 from casino.store_procurements where item_id=:id and status in ('PENDING','ORDERED')) as present",Map.of("id",id)).get("present"); }
    public boolean hasHistory(UUID id) { return (Boolean)required("select exists(select 1 from casino.store_request_lines where item_id=:id) or exists(select 1 from casino.store_movements where item_id=:id) as present",Map.of("id",id)).get("present"); }
    private <T> List<T> query(String sql,Map<String,?> args,Class<T> type) { return jdbc.query(sql,args,new DataClassRowMapper<>(type)); }
    private <T> Page<T> page(String sql,Map<String,Object> args,int page,int size,Class<T> type) {
        args.put("limit",size+1);args.put("offset",Math.multiplyExact(page,size));
        var rows=query(sql,args,type);boolean more=rows.size()>size;
        return new Page<>(List.copyOf(rows.subList(0,Math.min(size,rows.size()))),page,size,more);
    }
    public Page<Item> items(String q,Boolean active,int page,int size) {
        String sql="""
            select i.id,i.code,i.name,i.category,i.unit,i.active,i.quantity_balance,i.version,i.created_at,
              (exists(select 1 from casino.store_request_lines l where l.item_id=i.id) or
               exists(select 1 from casino.store_movements m where m.item_id=i.id)) as has_history,
              exists(select 1 from casino.store_movements m where m.item_id=i.id) as has_movements
            from casino.store_items i where (i.code ilike :q or i.name ilike :q or i.category ilike :q)
            """+(active==null?"":" and i.active=:active")+" order by i.created_at desc,i.id desc limit :limit offset :offset";
        return page(sql,params("q","%"+q+"%","active",active),page,size,Item.class);
    }
    public Page<Staff> staff(String q,int page,int size) {
        return page("""
            select s.id,s.employee_code,u.username as name,s.department_id,d.name as department_name
            from casino.staff_profiles s join casino.departments d on d.id=s.department_id
            join core.users u on u.id=s.user_id
            where s.employment_status='ACTIVE' and d.active=true and u.status='ACTIVE'
              and (s.employee_code ilike :q or u.username ilike :q)
            order by s.created_at desc,s.id desc limit :limit offset :offset
            """,params("q","%"+q+"%"),page,size,Staff.class);
    }
    private static final String REQUEST_SELECT="""
        select r.id,r.reference,r.department_id,d.name as department_name,r.requester_staff_profile_id,
          coalesce(requester.username,s.employee_code) as requester_name,r.recorded_by_user_id,
          coalesce(actor.username,'Unavailable') as recorded_by_name,r.created_at,r.required_date,r.remarks,r.status,
          r.version,r.cancelled_at,r.cancellation_reason
        from casino.store_requests r join casino.departments d on d.id=r.department_id
        join casino.staff_profiles s on s.id=r.requester_staff_profile_id
        left join core.users requester on requester.id=s.user_id left join core.users actor on actor.id=r.recorded_by_user_id
        """;
    public Page<Request> requests(String q,String status,int page,int size) {
        return page(REQUEST_SELECT+" where (r.reference ilike :q or d.name ilike :q or s.employee_code ilike :q) "
            +(status.isEmpty()?"":" and r.status=:status")+" order by r.created_at desc,r.id desc limit :limit offset :offset",
            params("q","%"+q+"%","status",status),page,size,Request.class);
    }
    public Detail detail(UUID id) {
        var headers=query(REQUEST_SELECT+" where r.id=:id",Map.of("id",id),Request.class);
        if(headers.isEmpty())throw new ResourceNotFoundException("Store request not found.");
        var lines=query("""
            select l.id,l.item_id,i.code as item_code,i.name as item_name,i.unit,i.active,
              l.requested_quantity,l.issued_quantity,l.cancelled_quantity,
              l.requested_quantity-l.issued_quantity-l.cancelled_quantity as outstanding_quantity,
              i.quantity_balance as available_quantity,p.id as procurement_id,p.reference as procurement_reference,p.status as procurement_status
            from casino.store_request_lines l join casino.store_items i on i.id=l.item_id
            left join casino.store_procurements p on p.request_line_id=l.id and p.status in ('PENDING','ORDERED')
            where l.request_id=:id order by i.code,l.id
            """,Map.of("id",id),Line.class);
        return new Detail(headers.getFirst(),lines);
    }
    public Page<Procurement> procurements(String q,String status,int page,int size) {
        return page("""
            select p.id,p.reference,p.request_line_id,l.request_id,r.reference as request_reference,d.name as department_name,
              p.item_id,i.code as item_code,i.name as item_name,i.unit,p.quantity,p.received_quantity,
              p.quantity-p.received_quantity as outstanding_quantity,p.status,p.supplier_reference,p.created_at,p.ordered_at,
              p.cancelled_at,p.cancellation_reason,p.version
            from casino.store_procurements p join casino.store_request_lines l on l.id=p.request_line_id
            join casino.store_requests r on r.id=l.request_id join casino.departments d on d.id=r.department_id
            join casino.store_items i on i.id=p.item_id
            where (p.reference ilike :q or r.reference ilike :q or i.code ilike :q or i.name ilike :q)
            """+(status.isEmpty()?"":" and p.status=:status")+" order by p.created_at desc,p.id desc limit :limit offset :offset",
            params("q","%"+q+"%","status",status),page,size,Procurement.class);
    }
    public Page<Movement> movements(String q,String type,UUID itemId,int page,int size) {
        return page("""
            select m.id,m.reference,m.item_id,i.code as item_code,i.name as item_name,i.unit,m.movement_type,m.quantity,
              m.balance_after,m.request_line_id,r.reference as request_reference,m.procurement_id,p.reference as procurement_reference,
              d.name as department_name,m.performed_by,coalesce(u.username,'Unavailable') as performed_by_name,m.performed_at,m.business_date,m.reason,m.external_reference
            from casino.store_movements m join casino.store_items i on i.id=m.item_id
            left join casino.store_procurements p on p.id=m.procurement_id
            left join casino.store_request_lines l on l.id=coalesce(m.request_line_id,p.request_line_id)
            left join casino.store_requests r on r.id=l.request_id
            left join casino.departments d on d.id=r.department_id left join core.users u on u.id=m.performed_by
            where (m.reference ilike :q or i.code ilike :q or i.name ilike :q)
            """+(type.isEmpty()?"":" and m.movement_type=:type")+(itemId==null?"":" and m.item_id=:item")+
            " order by m.ledger_order desc limit :limit offset :offset",params("q","%"+q+"%","type",type,"item",itemId),page,size,Movement.class);
    }
}
