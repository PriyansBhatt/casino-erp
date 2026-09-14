package com.casino.casinoerp.repository;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Scoped aggregate reads; no transaction entities or chip denomination collections. */
@Repository
public class CashierReconciliationReadRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public CashierReconciliationReadRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Tender(String source, String paymentMode, long count, BigDecimal amount) {}

    public List<Tender> tenders(UUID cashierId, LocalDate date) {
        return jdbc.query("""
                select 'BUY_IN' source, payment_mode, count(*) quantity, sum(amount_received) amount
                from cashier.chip_buy_ins where business_date = :date and created_by = :actor
                group by payment_mode
                union all
                select 'CASH_OUT', payment_mode, count(*), sum(cash_paid)
                from cashier.chip_cash_outs where business_date = :date and created_by = :actor
                group by payment_mode
                union all
                select 'LOSING_RETURN', payment_mode, count(*), sum(amount_paid)
                from cashier.losing_returns where business_date = :date and created_by = :actor
                group by payment_mode
                """, Map.of("date", date, "actor", cashierId), (rs, n) ->
                new Tender(rs.getString("source"), rs.getString("payment_mode"),
                        rs.getLong("quantity"), rs.getBigDecimal("amount")));
    }

    public boolean hasActivity(UUID cashierId, LocalDate date) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(
                    select 1 from cashier.chip_buy_ins where business_date = :date and created_by = :actor
                    union all
                    select 1 from cashier.chip_cash_outs where business_date = :date and created_by = :actor
                    union all
                    select 1 from cashier.losing_returns where business_date = :date and created_by = :actor
                )
                """, Map.of("date", date, "actor", cashierId), Boolean.class));
    }
}
