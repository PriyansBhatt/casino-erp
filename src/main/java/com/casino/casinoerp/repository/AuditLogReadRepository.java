package com.casino.casinoerp.repository;

import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.service.AuditDetailsPolicy;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
public class AuditLogReadRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public AuditLogReadRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public AuditLogPage read(int page, int size, LocalDateTime from, LocalDateTime to,
            LocalDate businessDate, String action, String module, UUID actorId, String search) {
        StringBuilder where = new StringBuilder(" where 1=1");
        MapSqlParameterSource args = new MapSqlParameterSource();
        if (from != null) { where.append(" and a.performed_at >= :from"); args.addValue("from", Timestamp.valueOf(from)); }
        if (to != null) { where.append(" and a.performed_at < :to"); args.addValue("to", Timestamp.valueOf(to)); }
        if (businessDate != null) { where.append(" and a.business_date = :businessDate"); args.addValue("businessDate", java.sql.Date.valueOf(businessDate)); }
        if (action != null) { where.append(" and a.action_type = :action"); args.addValue("action", action); }
        if (module != null) { where.append(" and a.module_name = :module"); args.addValue("module", module); }
        if (actorId != null) { where.append(" and a.performed_by = :actorId"); args.addValue("actorId", actorId); }
        if (search != null) {
            // strpos treats %, _ and backslashes literally. Remarks are deliberately excluded.
            where.append(" and (strpos(lower(coalesce(a.action_type,'')), :search)>0 or strpos(lower(coalesce(a.module_name,'')), :search)>0 or strpos(cast(a.entity_id as text), :search)>0 or strpos(lower(coalesce(u.username,'')), :search)>0 or strpos(lower(coalesce(u.full_name,'')), :search)>0)");
            args.addValue("search", search.toLowerCase(Locale.ROOT));
        }
        args.addValue("limit", size + 1).addValue("offset", (long) page * size);
        var rows = jdbc.query("""
                select a.id, a.business_date, a.action_type, a.module_name, a.entity_id,
                       a.performed_at, a.performed_by, a.remarks, u.username, u.full_name
                from audit.audit_logs a left join core.users u on u.id = a.performed_by
                """ + where + " order by a.performed_at desc nulls last, a.id desc limit :limit offset :offset", args,
                (rs, n) -> {
                    String actionType = rs.getString("action_type");
                    boolean withheld = !AuditDetailsPolicy.allows(actionType);
                    UUID actor = rs.getObject("performed_by", UUID.class);
                    Timestamp time = rs.getTimestamp("performed_at");
                    java.sql.Date date = rs.getDate("business_date");
                    return new AuditLogResponse(rs.getObject("id", UUID.class), date == null ? null : date.toLocalDate(),
                        actionType, rs.getString("module_name"), rs.getObject("entity_id", UUID.class),
                        time == null ? null : time.toLocalDateTime(), actor == null ? null :
                        new AuditLogResponse.Actor(actor, rs.getString("username"), rs.getString("full_name")),
                        withheld ? null : rs.getString("remarks"), withheld);
                });
        return new AuditLogPage(List.copyOf(rows.subList(0, Math.min(size, rows.size()))), page, size, rows.size() > size);
    }
}
