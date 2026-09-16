package com.casino.casinoerp.repository;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Financial responsibility comes from persisted activity, never current account role/status. */
@Repository
public class BusinessDateCloseReadRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public BusinessDateCloseReadRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Actor(UUID id, boolean hasOpening, String reconciliationStatus,
                        boolean legacyResolved) {}

    public List<Actor> actors(LocalDate date) {
        return jdbc.query("""
                with cash_activity as (
                    select created_by actor, amount_received received, 0::numeric paid
                    from cashier.chip_buy_ins
                    where business_date = :date and upper(trim(payment_mode)) = 'CASH'
                    union all
                    select created_by, 0::numeric, cash_paid from cashier.chip_cash_outs
                    where business_date = :date and upper(trim(payment_mode)) = 'CASH'
                    union all
                    select created_by, 0::numeric, amount_paid from cashier.losing_returns
                    where business_date = :date and upper(trim(payment_mode)) = 'CASH'
                ), totals as (
                    select actor, sum(received) received, sum(paid) paid
                    from cash_activity group by actor
                ), actors as (
                    select actor from totals
                    union
                    select cashier_user_id from cashier.cashier_opening_balances where business_date = :date
                    union
                    select cashier_user_id from cashier.cashier_reconciliations where business_date = :date
                )
                select a.actor, o.id is not null has_opening, r.lifecycle_status,
                    coalesce(t.received = l.cash_received and t.paid = l.cash_paid
                        and t.received - t.paid = l.net_cash_movement, false) legacy_resolved
                from actors a
                left join totals t on t.actor = a.actor
                left join cashier.cashier_opening_balances o
                    on o.cashier_user_id = a.actor and o.business_date = :date
                left join cashier.cashier_reconciliations r
                    on r.cashier_user_id = a.actor and r.business_date = :date
                left join cashier.legacy_cash_actor_resolutions l
                    on l.actor_user_id = a.actor and l.business_date = :date
                """, Map.of("date", date), (rs, n) -> new Actor(
                rs.getObject("actor", UUID.class), rs.getBoolean("has_opening"),
                rs.getString("lifecycle_status"), rs.getBoolean("legacy_resolved")));
    }
}
