package com.casino.casinoerp.repository;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.*;

/** One scoped aggregate read, independent of transaction/denomination entity hydration. */
@Repository
public class PitReadRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public PitReadRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record Totals(long players, BigDecimal chipIn, BigDecimal wins, BigDecimal losses) {
        public static final Totals ZERO = new Totals(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
    public Map<UUID, Totals> summarize(List<UUID> operationIds) {
        if (operationIds.isEmpty()) return Map.of();
        Map<UUID, Totals> totals = new HashMap<>();
        jdbc.query("""
                with players as (
                    select pit_table_id, count(*) players from casino.pit_table_customer_assignments
                    where pit_table_id in (:ids) and status = 'ACTIVE' group by pit_table_id
                ), chips as (
                    select pit_table_id, sum(total_value) chip_in from cashier.chip_custody_movements
                    where pit_table_id in (:ids) and movement_type = 'CUSTOMER_TO_TABLE' group by pit_table_id
                ), results as (
                    select pit_table_id,
                        coalesce(sum(amount) filter (where result_type = 'WIN'), 0) wins,
                        coalesce(sum(amount) filter (where result_type = 'LOSS'), 0) losses
                    from casino.verified_gaming_results where pit_table_id in (:ids) group by pit_table_id
                )
                select t.id, coalesce(p.players,0) players, coalesce(c.chip_in,0) chip_in,
                       coalesce(r.wins,0) wins, coalesce(r.losses,0) losses
                from casino.pit_tables t
                left join players p on p.pit_table_id=t.id
                left join chips c on c.pit_table_id=t.id
                left join results r on r.pit_table_id=t.id
                where t.id in (:ids)
                """, Map.of("ids", operationIds), rs -> {
            totals.put(rs.getObject("id", UUID.class), new Totals(rs.getLong("players"),
                    rs.getBigDecimal("chip_in"), rs.getBigDecimal("wins"), rs.getBigDecimal("losses")));
        });
        return totals;
    }
}
