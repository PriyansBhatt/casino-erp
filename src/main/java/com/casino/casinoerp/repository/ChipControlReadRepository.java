package com.casino.casinoerp.repository;

import com.casino.casinoerp.dto.ChipControlSessionResponse;
import com.casino.casinoerp.dto.ChipCustodyDisplayResponse;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.*;

/** Purpose-built reads: aggregate each ledger before joining, avoiding multiplicative totals. */
@Repository
public class ChipControlReadRepository {
    public static final String DIRECTORY_SQL = """
        with selected as (
            select * from session.customer_sessions
            where upper(status) = 'OPEN' and exit_time is null and business_date = :businessDate
        ), buyins as (
            select customer_session_id, sum(total_chip_value_issued) total
            from cashier.chip_buy_ins where customer_session_id in (select id from selected)
            group by customer_session_id
        ), cashouts as (
            select customer_session_id, sum(total_chip_value_returned) total
            from cashier.chip_cash_outs where customer_session_id in (select id from selected)
            group by customer_session_id
        ), results as (
            select customer_session_id,
                coalesce(sum(amount) filter (where result_type = 'WIN'), 0) wins,
                coalesce(sum(amount) filter (where result_type = 'LOSS'), 0) losses
            from casino.verified_gaming_results where customer_session_id in (select id from selected)
            group by customer_session_id
        )
        select s.id, s.customer_id, s.session_code, s.business_date, s.entry_time, s.status,
            c.customer_code, c.full_name, a.pit_table_id, t.table_code, t.table_name,
            coalesce(b.total, 0) buyin, coalesce(o.total, 0) cashout,
            coalesce(r.wins, 0) wins, coalesce(r.losses, 0) losses,
            coalesce(b.total, 0) + coalesce(r.wins, 0) - coalesce(r.losses, 0) - coalesce(o.total, 0) position
        from selected s join customer.customers c on c.id = s.customer_id
        left join buyins b on b.customer_session_id = s.id
        left join cashouts o on o.customer_session_id = s.id
        left join results r on r.customer_session_id = s.id
        left join casino.pit_table_customer_assignments a on a.customer_session_id = s.id and a.status = 'ACTIVE'
        left join casino.pit_tables t on t.id = a.pit_table_id
        order by s.entry_time asc, s.id asc
        """;

    private final NamedParameterJdbcTemplate jdbc;
    public ChipControlReadRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<ChipControlSessionResponse> directory(LocalDate date) {
        return jdbc.query(DIRECTORY_SQL, Map.of("businessDate", date), (row, index) -> {
            var tableId = row.getObject("pit_table_id", UUID.class);
            var position = row.getBigDecimal("position");
            String status = tableId != null ? "AT_TABLE" : position.signum() > 0 ? "OUTSTANDING"
                    : position.signum() < 0 ? "NEGATIVE_POSITION" : "CLEAR";
            return new ChipControlSessionResponse(row.getObject("customer_id", UUID.class),
                    row.getString("customer_code"), row.getString("full_name"), row.getObject("id", UUID.class),
                    row.getString("session_code"), row.getObject("business_date", LocalDate.class),
                    row.getTimestamp("entry_time") == null ? null : row.getTimestamp("entry_time").toLocalDateTime(),
                    row.getString("status"), null, tableId, row.getString("table_code"), row.getString("table_name"),
                    row.getBigDecimal("buyin"), row.getBigDecimal("wins"), row.getBigDecimal("losses"),
                    row.getBigDecimal("cashout"), position, status);
        });
    }

    public Map<UUID, ChipCustodyDisplayResponse> movementDisplays(Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        var values = jdbc.query("""
            select m.id, c.customer_code, c.full_name customer_name, s.session_code,
                   t.table_code, t.table_name, u.username, u.full_name actor_name
            from cashier.chip_custody_movements m
            left join session.customer_sessions s on s.id = m.customer_session_id
            left join customer.customers c on c.id = s.customer_id
            left join casino.pit_tables t on t.id = m.pit_table_id
            left join core.users u on u.id = m.created_by
            where m.id in (:ids)
            """, Map.of("ids", ids), (row, index) -> Map.entry(row.getObject("id", UUID.class),
                new ChipCustodyDisplayResponse(row.getString("customer_code"), row.getString("customer_name"),
                    row.getString("session_code"), row.getString("table_code"), row.getString("table_name"),
                    row.getString("username"), row.getString("actor_name"))));
        Map<UUID, ChipCustodyDisplayResponse> result = new HashMap<>();
        values.forEach(value -> result.put(value.getKey(), value.getValue()));
        return result;
    }
}
