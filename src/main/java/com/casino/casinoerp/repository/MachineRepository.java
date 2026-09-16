package com.casino.casinoerp.repository;

import com.casino.casinoerp.dto.MachineDtos.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Repository
public class MachineRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public MachineRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }
    public boolean hasActivePlay(UUID sessionId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from casino.slot_plays
                    where customer_session_id = :sessionId and status = 'ACTIVE')
                """, Map.of("sessionId", sessionId), Boolean.class));
    }

    public record StoredMachine(UUID id, String type, String availability) {}
    public record StoredPlay(UUID id, UUID machineId, UUID customerId, UUID sessionId, LocalDate date,
            String status, UUID startedBy, UUID endedBy, String startKey, String endKey) {}
    private static final String PLAY_COLUMNS = """
        p.id play_id, p.machine_id, p.customer_id, c.customer_code, c.full_name customer_name,
        p.customer_session_id, s.session_code, p.business_date, p.status play_status,
        p.started_at, p.ended_at, p.started_by, p.ended_by
        """;
    private static final String MACHINE_READ = """
        select m.*, %s from casino.gaming_machines m
        left join casino.slot_plays p on p.machine_id=m.id and p.status='ACTIVE'
        left join customer.customers c on c.id=p.customer_id
        left join session.customer_sessions s on s.id=p.customer_session_id
        """.formatted(PLAY_COLUMNS);
    public List<Machine> overview() {
        return jdbc.query(MACHINE_READ+" order by m.machine_code, m.id", Map.of(), (rs,n)->machine(rs));
    }
    public Optional<Machine> detail(UUID id) {
        return jdbc.query(MACHINE_READ+" where m.id=:id", Map.of("id",id), (rs,n)->machine(rs)).stream().findFirst();
    }
    public List<Play> history(UUID id) {
        return jdbc.query("select "+PLAY_COLUMNS+"""
             from casino.slot_plays p join customer.customers c on c.id=p.customer_id
             join session.customer_sessions s on s.id=p.customer_session_id
             where p.machine_id=:id and p.status='ENDED' order by p.started_at desc,p.id desc limit 50
             """,Map.of("id",id),(rs,n)->play(rs));
    }
    public List<Candidate> candidates(String query, LocalDate date) {
        return jdbc.query("""
            select c.id, c.customer_code, c.full_name, s.id session_id, s.session_code, s.business_date
            from customer.customers c join session.customer_sessions s on s.customer_id=c.id
            where c.status='ACTIVE' and upper(s.status)='OPEN' and s.exit_time is null and s.business_date=:date
            and (lower(c.customer_code) like :query or lower(c.full_name) like :query)
            and not exists(select 1 from casino.slot_plays p where p.customer_session_id=s.id and p.status='ACTIVE')
            order by c.customer_code,s.id limit 20
            """,Map.of("date",date,"query","%"+query.toLowerCase(Locale.ROOT)+"%"),(r,n)->new Candidate(uuid(r,"id"),r.getString("customer_code"),r.getString("full_name"),uuid(r,"session_id"),r.getString("session_code"),r.getObject("business_date",LocalDate.class)));
    }
    public void lockCustomer(UUID id) {
        jdbc.query("select id from customer.customers where id=:id for update",Map.of("id",id),(r,n)->uuid(r,"id"));
    }
    public Optional<StoredMachine> lock(UUID id) {
        return jdbc.query("select id,machine_type,availability from casino.gaming_machines where id=:id for update",
                Map.of("id",id),(r,n)->new StoredMachine(uuid(r,"id"),r.getString("machine_type"),r.getString("availability"))).stream().findFirst();
    }
    public boolean occupied(UUID machine, UUID session) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            select exists(select 1 from casino.slot_plays where status='ACTIVE'
              and (machine_id=:machine or customer_session_id=:session))
            """,Map.of("machine",machine,"session",session),Boolean.class));
    }
    public Optional<StoredPlay> byKey(String key, boolean end) {
        return stored("where "+(end?"end_key":"start_key")+"=:value",key);
    }
    public Optional<StoredPlay> play(UUID id) { return stored("where id=:value for update",id); }
    private Optional<StoredPlay> stored(String clause,Object value) {
        return jdbc.query("select * from casino.slot_plays "+clause,Map.of("value",value),(r,n)->new StoredPlay(
            uuid(r,"id"),uuid(r,"machine_id"),uuid(r,"customer_id"),uuid(r,"customer_session_id"),r.getObject("business_date",LocalDate.class),
            r.getString("status"),uuid(r,"started_by"),uuid(r,"ended_by"),r.getString("start_key"),r.getString("end_key"))).stream().findFirst();
    }
    public void create(UUID id, Create request, UUID actor, LocalDateTime now) {
        Map<String,Object> p=new HashMap<>();p.put("id",id);p.put("code",request.machineCode().toUpperCase(Locale.ROOT));
        p.put("name",request.displayName().trim());p.put("type",request.machineType().name());
        p.put("location",request.location()==null?null:request.location().trim());p.put("actor",actor);p.put("now",now);
        jdbc.update("""
            insert into casino.gaming_machines(id,machine_code,display_name,machine_type,location,availability,created_at,updated_at,created_by)
            values(:id,:code,:name,:type,:location,'AVAILABLE',:now,:now,:actor)
            """,p);
    }
    public void status(UUID id, String status, LocalDateTime now) {
        jdbc.update("update casino.gaming_machines set availability=:status,updated_at=:now where id=:id",Map.of("id",id,"status",status,"now",now));
    }
    public void start(UUID id, UUID machine, Start input, UUID actor, LocalDate date, LocalDateTime now) {
        jdbc.update("""
            insert into casino.slot_plays(id,machine_id,customer_id,customer_session_id,business_date,status,started_at,started_by,start_key)
            values(:id,:machine,:customer,:session,:date,'ACTIVE',:now,:actor,:key)
            """,Map.of("id",id,"machine",machine,"customer",input.customerId(),"session",input.customerSessionId(),"date",date,"now",now,"actor",actor,"key",input.idempotencyKey().trim()));
        status(machine,"AVAILABLE",now);
    }
    public void end(StoredPlay play, End input, UUID actor, LocalDateTime now) {
        jdbc.update("update casino.slot_plays set status='ENDED',ended_at=:now,ended_by=:actor,end_key=:key where id=:id",
            Map.of("id",play.id(),"now",now,"actor",actor,"key",input.idempotencyKey().trim()));
        status(play.machineId(),"AVAILABLE",now);
    }
    private Machine machine(ResultSet r) throws SQLException {
        Play active=uuid(r,"play_id")==null?null:play(r);
        return new Machine(uuid(r,"id"),r.getString("machine_code"),r.getString("display_name"),Type.valueOf(r.getString("machine_type")),
            r.getString("location"),active==null?r.getString("availability"):"IN_USE",r.getObject("created_at",LocalDateTime.class),r.getObject("updated_at",LocalDateTime.class),active);
    }
    private Play play(ResultSet r) throws SQLException {
        return new Play(uuid(r,"play_id"),uuid(r,"machine_id"),uuid(r,"customer_id"),r.getString("customer_code"),r.getString("customer_name"),
            uuid(r,"customer_session_id"),r.getString("session_code"),r.getObject("business_date",LocalDate.class),r.getString("play_status"),
            r.getObject("started_at",LocalDateTime.class),r.getObject("ended_at",LocalDateTime.class),uuid(r,"started_by"),uuid(r,"ended_by"));
    }
    private UUID uuid(ResultSet r,String name) throws SQLException { return r.getObject(name,UUID.class); }
}
