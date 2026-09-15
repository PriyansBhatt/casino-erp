package com.casino.casinoerp.repository;
import com.casino.casinoerp.dto.CrmDtos.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import java.time.*;
import java.util.*;
@Repository
public class CrmRepository {
 private final NamedParameterJdbcTemplate jdbc;
 public CrmRepository(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}
 // Fixed SQL fragments only; no user-controlled identifiers.
 private static final String RECORDS="""
 SELECT h.id,'HOTEL' AS "recordType",h.booking_code AS reference,h.customer_id,h.customer_session_id,
 h.business_date,h.check_in_date::timestamp AS "recordAt",h.hotel_name || ' / ' || h.room_type AS description,
 coalesce(h.actual_cost,h.estimated_cost) AS cost,CASE WHEN h.actual_cost IS NULL THEN 'ESTIMATE' ELSE 'ACTUAL' END AS "costBasis",
 h.status,h.created_by AS actor,h.created_at,0::bigint AS version,
 h.remarks AS notes,h.billing_type AS classification,NULL::text AS "transportType",NULL::text AS pickup,NULL::text AS destination,NULL::text AS vehicle,NULL::text AS driver
 FROM customer.hotel_bookings h
 UNION ALL
 SELECT t.id,'TRANSPORT',t.reference,t.customer_id,t.customer_session_id,t.business_date,t.scheduled_at,
 t.pickup || ' → ' || t.destination,t.cost,'RECORDED',t.status,t.recorded_by,t.created_at,t.version,
 t.notes,NULL,t.transport_type,t.pickup,t.destination,t.vehicle,t.driver FROM customer.transport_records t
 UNION ALL
 SELECT s.id,s.service_type,'SRV-' || s.id::text,s.customer_id,s.customer_session_id,s.business_date,s.service_at,
 s.service_description,s.service_cost,'RECORDED',s.status,s.recorded_by,s.created_at,s.version,
 s.remarks,s.classification,NULL,NULL,NULL,NULL,NULL FROM customer.customer_service_records s WHERE s.crm_record
 """;
 public List<Map<String,Object>> history(String q,String type,String status,LocalDate from,LocalDate to){
  var p=new MapSqlParameterSource().addValue("q",q).addValue("type",type).addValue("status",status)
   .addValue("from",from==null?null:from.atStartOfDay(),java.sql.Types.TIMESTAMP)
   .addValue("to",to==null?null:to.plusDays(1).atStartOfDay(),java.sql.Types.TIMESTAMP);
  return jdbc.queryForList("SELECT r.*,c.customer_code AS \"customerCode\",c.full_name AS \"customerName\",u.username AS staff FROM ("+RECORDS+") r LEFT JOIN customer.customers c ON c.id=r.customer_id LEFT JOIN core.users u ON u.id=r.actor WHERE (:type='' OR r.\"recordType\"=:type OR (:type='SERVICE' AND r.\"recordType\" IN ('GIFT','FOOD','TICKET','OTHER'))) AND (:status='' OR r.status=:status) AND (cast(:from as timestamp) IS NULL OR r.\"recordAt\">=:from) AND (cast(:to as timestamp) IS NULL OR r.\"recordAt\"<:to) AND (:q='' OR lower(coalesce(c.customer_code,'') || ' ' || coalesce(c.full_name,'') || ' ' || r.description || ' ' || r.reference) LIKE :q) ORDER BY r.\"recordAt\" DESC,r.\"recordType\",r.id DESC LIMIT 100",p);
 }
 public List<Map<String,Object>> customers(String q){return jdbc.queryForList("SELECT id,customer_code AS \"customerCode\",full_name AS \"fullName\" FROM customer.customers WHERE lower(customer_code || ' ' || full_name) LIKE :q ORDER BY customer_code,id LIMIT 20",Map.of("q","%"+q.toLowerCase()+"%"));}
 public void retryLock(String key){jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(:key,3301))",Map.of("key",key));}
 public Map<String,Object> replay(boolean transport,String key){var rows=jdbc.queryForList("SELECT id,request_signature FROM "+table(transport)+" WHERE idempotency_key=:key"+(transport?"":" AND crm_record"),Map.of("key",key));return rows.isEmpty()?null:rows.getFirst();}
 public UUID create(ServiceCreate r,UUID actor,LocalDate date,LocalDateTime now,String signature){
  UUID id=UUID.randomUUID();var p=base(id,r.customerId(),r.customerSessionId(),actor,date,now,r.idempotencyKey(),signature)
   .addValue("type",r.serviceType().name()).addValue("description",r.description().trim()).addValue("at",r.serviceAt())
   .addValue("cost",r.cost()).addValue("classification",r.classification().name()).addValue("notes",r.notes());
  jdbc.update("INSERT INTO customer.customer_service_records(id,customer_id,customer_session_id,business_date,created_at,updated_at,recorded_by,idempotency_key,request_signature,crm_record,service_type,service_description,service_at,service_cost,classification,remarks,status) VALUES(:id,:customer,:session,:date,:now,:now,:actor,:key,:signature,true,:type,:description,:at,:cost,:classification,:notes,'COMPLETED')",p);return id;
 }
 public UUID create(TransportCreate r,UUID actor,LocalDate date,LocalDateTime now,String signature){
  UUID id=UUID.randomUUID();var p=base(id,r.customerId(),r.customerSessionId(),actor,date,now,r.idempotencyKey(),signature)
   .addValue("reference","TR-"+id).addValue("type",r.transportType().name()).addValue("pickup",r.pickup().trim()).addValue("destination",r.destination().trim())
   .addValue("at",r.scheduledAt()).addValue("cost",r.cost()).addValue("vehicle",r.vehicle()).addValue("driver",r.driver()).addValue("notes",r.notes());
  jdbc.update("INSERT INTO customer.transport_records(id,reference,customer_id,customer_session_id,business_date,created_at,updated_at,recorded_by,idempotency_key,request_signature,transport_type,pickup,destination,scheduled_at,cost,vehicle,driver,notes,status) VALUES(:id,:reference,:customer,:session,:date,:now,:now,:actor,:key,:signature,:type,:pickup,:destination,:at,:cost,:vehicle,:driver,:notes,'SCHEDULED')",p);return id;
 }
 private MapSqlParameterSource base(UUID id,UUID customer,UUID session,UUID actor,LocalDate date,LocalDateTime now,String key,String signature){return new MapSqlParameterSource().addValue("id",id).addValue("customer",customer).addValue("session",session).addValue("actor",actor).addValue("date",date).addValue("now",now).addValue("key",key.trim()).addValue("signature",signature);}
 private String table(boolean transport){return transport?"customer.transport_records":"customer.customer_service_records";}
 public boolean changedAlready(boolean transport,UUID id,Change r){return jdbc.queryForList("SELECT id FROM "+table(transport)+" WHERE id=:id AND version=:version AND status=:status"+(transport?"":" AND crm_record"),Map.of("id",id,"version",r.expectedVersion()+1,"status",r.status())).size()==1;}
 public int change(boolean transport,UUID id,Change r,LocalDateTime now){return jdbc.update("UPDATE "+table(transport)+" SET status=:status,version=version+1,updated_at=:now WHERE id=:id AND version=:version AND status=:previous"+(transport?"":" AND crm_record"),Map.of("status",r.status(),"now",now,"id",id,"version",r.expectedVersion(),"previous",transport?"SCHEDULED":"COMPLETED"));}
}
