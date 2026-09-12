package com.casino.casinoerp.repository;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/** Read projections only: never hydrate transaction entities or their denomination collections. */
@Repository
public class ManagementDashboardRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public ManagementDashboardRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record PaymentTotals(BigDecimal buyIn, BigDecimal cashOut, BigDecimal losingReturnPaid) {}
    public record ReconciliationAggregate(long submittedCount, BigDecimal variance) {}

    public PaymentTotals payments(LocalDate date) {
        // These tables hold completed postings, not pending requests. Match the all-tender totals
        // in RunningFundsReportService using scalar SQL aggregates instead of hydrating entities.
        return jdbc.queryForObject("""
                select
                    (select coalesce(sum(amount_received), 0) from cashier.chip_buy_ins
                        where business_date = :date) as buy_in,
                    (select coalesce(sum(cash_paid), 0) from cashier.chip_cash_outs
                        where business_date = :date) as cash_out,
                    (select coalesce(sum(amount_paid), 0) from cashier.losing_returns
                        where business_date = :date) as losing_return_paid
                """, Map.of("date", date), (rs, row) -> new PaymentTotals(
                rs.getBigDecimal("buy_in"), rs.getBigDecimal("cash_out"), rs.getBigDecimal("losing_return_paid")));
    }

    public long activeCustomers(LocalDate date) {
        return jdbc.queryForObject("""
                select count(distinct customer_id) from session.customer_sessions
                where business_date = :date and upper(status) = 'OPEN' and exit_time is null
                """, Map.of("date", date), Long.class);
    }

    public long activeTables(LocalDate date) {
        return jdbc.queryForObject("""
                select count(distinct p.id) from casino.physical_pit_tables p
                join casino.pit_tables t on t.physical_table_id = p.id
                where t.business_date = :date and upper(t.status) = 'OPEN' and t.closed_at is null
                """, Map.of("date", date), Long.class);
    }

    public ReconciliationAggregate reconciliations(LocalDate date) {
        // Same submitted-only semantics as RunningFundsReportService; reopened snapshots are excluded.
        return jdbc.queryForObject("""
                select count(*) as submitted_count, coalesce(sum(variance), 0) as variance
                from cashier.cashier_reconciliations
                where business_date = :date and lifecycle_status = 'SUBMITTED'
                """, Map.of("date", date), (rs, row) -> new ReconciliationAggregate(
                rs.getLong("submitted_count"), rs.getBigDecimal("variance")));
    }
}
