package com.casino.casinoerp.repository;

import com.casino.casinoerp.dto.FnbDtos.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import java.time.*;
import java.util.*;

@Repository
public class FnbRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public FnbRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }
    private static final String PROJECTION = """
        SELECT r.*, c.customer_code, c.full_name, u.username requester, h.username handler
        FROM customer.fnb_requests r JOIN customer.customers c ON c.id=r.customer_id
        JOIN core.users u ON u.id=r.requested_by LEFT JOIN core.users h ON h.id=r.handled_by
        """;
    public List<Request> history(LocalDate date,LocalDate from,LocalDate to,String type,String status,String q,boolean live) {
        var args=new MapSqlParameterSource().addValue("date",date,java.sql.Types.DATE)
            .addValue("from",from,java.sql.Types.DATE).addValue("to",to,java.sql.Types.DATE)
            .addValue("type",type).addValue("status",status).addValue("q",q.isBlank()?"":"%"+q.trim().toLowerCase(Locale.ROOT)+"%");
        return jdbc.query(PROJECTION + """
            WHERE (cast(:date as date) IS NULL OR r.business_date=:date)
            AND (cast(:from as date) IS NULL OR r.business_date>=:from)
            AND (cast(:to as date) IS NULL OR r.business_date<=:to)
            AND (:type='' OR r.request_type=:type) AND (:status='' OR r.status=:status)
            AND (:q='' OR lower(c.customer_code || ' ' || c.full_name || ' ' || r.item || ' ' || r.location) LIKE :q)
            """ + (live ? " AND r.status IN ('PENDING','PREPARING','READY') " : "")
            + " ORDER BY r.requested_at DESC,r.id DESC LIMIT 100",args,(r,n)->new Request(
                r.getObject("id",UUID.class),r.getObject("business_date",LocalDate.class),r.getObject("requested_at",LocalDateTime.class),r.getObject("delivered_at",LocalDateTime.class),r.getObject("delivered_business_date",LocalDate.class),
                r.getObject("customer_id",UUID.class),r.getString("customer_code"),r.getString("full_name"),r.getObject("customer_session_id",UUID.class),
                Type.valueOf(r.getString("request_type")),r.getString("item"),r.getInt("quantity"),r.getString("location"),r.getString("request_type").equals("FOOD")?"KITCHEN":"BAR",Status.valueOf(r.getString("status")),
                r.getObject("requested_by",UUID.class),r.getString("requester"),r.getObject("handled_by",UUID.class),r.getString("handler"),r.getString("remarks"),r.getInt("version")));
    }
    // Live backlog spans rollover. Delivered metrics use the persisted operational date at delivery.
    public Map<String,Long> overview(LocalDate date) {
        var rows=jdbc.queryForMap("""
            SELECT count(*) FILTER(WHERE status IN ('PENDING','PREPARING','READY')) AS active,
            count(*) FILTER(WHERE status='PENDING') AS pending,
            count(*) FILTER(WHERE status='PREPARING') AS preparing,
            count(*) FILTER(WHERE status='READY') AS ready,
            count(*) FILTER(WHERE status='DELIVERED' AND delivered_business_date=:date) AS delivered,
            coalesce(sum(quantity::bigint) FILTER(WHERE status='DELIVERED' AND request_type='FOOD' AND delivered_business_date=:date),0) AS food,
            coalesce(sum(quantity::bigint) FILTER(WHERE status='DELIVERED' AND request_type='BEVERAGE' AND delivered_business_date=:date),0) AS beverage
            FROM customer.fnb_requests WHERE delivered_business_date=:date OR status IN ('PENDING','PREPARING','READY')
            """,Map.of("date",date));
        Map<String,Long> result=new LinkedHashMap<>();rows.forEach((k,v)->result.put(k,((Number)v).longValue()));return result;
    }
    public List<Map<String,Object>> customers(String q) {
        return jdbc.queryForList("SELECT id,customer_code AS \"customerCode\",full_name AS \"fullName\" FROM customer.customers WHERE lower(customer_code || ' ' || full_name) LIKE :q ORDER BY customer_code,id LIMIT 20",Map.of("q","%"+q.toLowerCase(Locale.ROOT)+"%"));
    }
    public void retryLock(String key) { jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(:key,3501))",Map.of("key",key)); }
    public Map<String,Object> replay(String key) {
        var rows=jdbc.queryForList("SELECT id,request_signature FROM customer.fnb_requests WHERE idempotency_key=:key",Map.of("key",key));
        return rows.isEmpty()?null:rows.getFirst();
    }
    public UUID create(Create r,UUID actor,LocalDate date,LocalDateTime now,String signature) {
        UUID id=UUID.randomUUID();
        var args=new MapSqlParameterSource().addValue("id",id).addValue("customer",r.customerId()).addValue("session",r.customerSessionId())
            .addValue("date",date).addValue("now",now).addValue("type",r.type().name()).addValue("item",r.item().trim())
            .addValue("quantity",r.quantity()).addValue("location",r.location().trim()).addValue("remarks",r.remarks())
            .addValue("actor",actor).addValue("key",r.idempotencyKey().trim()).addValue("signature",signature);
        jdbc.update("""
            INSERT INTO customer.fnb_requests(id,customer_id,customer_session_id,business_date,requested_at,request_type,item,quantity,location,remarks,status,requested_by,updated_at,idempotency_key,request_signature)
            VALUES(:id,:customer,:session,:date,:now,:type,:item,:quantity,:location,:remarks,'PENDING',:actor,:now,:key,:signature)
            """,args);
        event(id,0,Status.PENDING,actor,now);return id;
    }
    public Map<String,Object> lock(UUID id) {
        var rows=jdbc.queryForList("SELECT status,version FROM customer.fnb_requests WHERE id=:id FOR UPDATE",Map.of("id",id));return rows.isEmpty()?null:rows.getFirst();
    }
    public boolean replayChange(UUID id,Change r,UUID actor) {
        return !jdbc.queryForList("SELECT 1 FROM customer.fnb_status_events WHERE request_id=:id AND version=:version AND status=:status AND actor_id=:actor",
            Map.of("id",id,"version",r.expectedVersion()+1,"status",r.status().name(),"actor",actor)).isEmpty();
    }
    public void change(UUID id,Change r,UUID actor,LocalDateTime now,LocalDate deliveryDate) {
        var args=new MapSqlParameterSource().addValue("id",id).addValue("status",r.status().name()).addValue("actor",actor).addValue("now",now)
            .addValue("deliveryDate",r.status()==Status.DELIVERED?deliveryDate:null,java.sql.Types.DATE)
            .addValue("delivered",r.status()==Status.DELIVERED?now:null,java.sql.Types.TIMESTAMP);
        jdbc.update("UPDATE customer.fnb_requests SET status=:status,version=version+1,handled_by=:actor,updated_at=:now,delivered_at=:delivered,delivered_business_date=:deliveryDate WHERE id=:id",args);
        event(id,r.expectedVersion()+1,r.status(),actor,now);
    }
    private void event(UUID id,int version,Status status,UUID actor,LocalDateTime now) {
        jdbc.update("INSERT INTO customer.fnb_status_events(request_id,version,status,actor_id,changed_at) VALUES(:id,:version,:status,:actor,:now)",
            Map.of("id",id,"version",version,"status",status.name(),"actor",actor,"now",now));
    }
}
