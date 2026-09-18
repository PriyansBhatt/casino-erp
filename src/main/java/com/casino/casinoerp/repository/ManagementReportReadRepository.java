package com.casino.casinoerp.repository;

import com.casino.casinoerp.dto.RunningFundsReconciliationResponse;
import com.casino.casinoerp.dto.RunningFundsReportResponse.Tender;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** Fixed-count scalar/grouped SQL and a bounded joined page; no entity hydration. */
@Repository
public class ManagementReportReadRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public ManagementReportReadRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record Guests(long entries, long distinctGuests) {}
    public record Payments(Map<String, Tender> buyIn, Map<String, Tender> cashOut) {}
    public record Payouts(long count, BigDecimal amount) {}
    public record Gaming(BigDecimal wins, BigDecimal losses) {}
    public record Reconciliations(long submitted, long reopened, BigDecimal variance) {}

    public Guests guests(LocalDate date) {
        return jdbc.queryForObject("""
                select count(*) entries, count(distinct customer_id) guests
                from session.customer_sessions where business_date = :date
                """, Map.of("date", date), (r,n) -> new Guests(r.getLong("entries"), r.getLong("guests")));
    }
    public Payments payments(LocalDate date) {
        Map<String, Tender> buyIn = emptyTenders(), cashOut = emptyTenders();
        jdbc.query("""
                select 'BUY_IN' source, payment_mode, count(*) quantity, count(amount_received) valued, sum(amount_received) amount
                from cashier.chip_buy_ins where business_date = :date group by payment_mode
                union all
                select 'CASH_OUT', payment_mode, count(*), count(cash_paid), sum(cash_paid)
                from cashier.chip_cash_outs where business_date = :date group by payment_mode
                """, Map.of("date", date), r -> {
            var target = "BUY_IN".equals(r.getString("source")) ? buyIn : cashOut;
            String mode = r.getString("payment_mode");
            if (!target.containsKey(mode) || r.getLong("valued") != r.getLong("quantity") || r.getBigDecimal("amount") == null)
                throw new IllegalStateException("Report tender data is unsupported or incomplete.");
            target.put(mode, new Tender(r.getLong("quantity"), r.getBigDecimal("amount")));
        });
        return new Payments(Collections.unmodifiableMap(buyIn), Collections.unmodifiableMap(cashOut));
    }
    private Map<String, Tender> emptyTenders() {
        Map<String, Tender> result = new LinkedHashMap<>();
        for (String mode : List.of("CASH", "BANK", "CARD", "QR")) result.put(mode, new Tender(0, BigDecimal.ZERO));
        return result;
    }
    public Payouts payouts(LocalDate date) {
        return jdbc.queryForObject("""
                select count(*) quantity, coalesce(sum(amount_paid),0) amount
                from cashier.losing_returns where business_date = :date
                """, Map.of("date", date), (r,n) -> new Payouts(r.getLong("quantity"), r.getBigDecimal("amount")));
    }
    public Gaming gaming(LocalDate date) {
        return jdbc.queryForObject("""
                select coalesce(sum(amount) filter(where result_type='WIN'),0) wins,
                       coalesce(sum(amount) filter(where result_type='LOSS'),0) losses
                from casino.verified_gaming_results where business_date = :date
                """, Map.of("date", date), (r,n) -> new Gaming(r.getBigDecimal("wins"), r.getBigDecimal("losses")));
    }
    public Reconciliations reconciliations(LocalDate date) {
        return jdbc.queryForObject("""
                select count(*) filter(where lifecycle_status='SUBMITTED') submitted,
                       count(*) filter(where lifecycle_status='REOPENED') reopened,
                       sum(variance) filter(where lifecycle_status='SUBMITTED') variance
                from cashier.cashier_reconciliations where business_date = :date
                """, Map.of("date", date), (r,n) -> new Reconciliations(r.getLong("submitted"), r.getLong("reopened"), r.getBigDecimal("variance")));
    }
    public List<RunningFundsReconciliationResponse> rows(LocalDate date, int page, int size) {
        return jdbc.query("""
                select r.id, r.business_date, r.cashier_user_id, u.username, u.full_name,
                       r.lifecycle_status,
                       case when r.lifecycle_status='SUBMITTED' then r.status end status,
                       r.opening_cash, r.expected_closing_cash,
                       case when r.lifecycle_status='SUBMITTED' then r.actual_closing_cash end actual_closing_cash,
                       case when r.lifecycle_status='SUBMITTED' then r.variance end variance,
                       r.submitted_at, r.reopened_at
                from cashier.cashier_reconciliations r
                left join core.users u on u.id = r.cashier_user_id
                where r.business_date = :date
                order by r.submitted_at desc nulls last, r.id desc
                limit :limit offset :offset
                """, Map.of("date", date, "limit", size + 1, "offset", (long) page * size), (r,n) ->
                new RunningFundsReconciliationResponse(r.getObject("id", UUID.class), r.getObject("business_date", LocalDate.class),
                        r.getObject("cashier_user_id", UUID.class), r.getString("username"), r.getString("full_name"),
                        r.getString("lifecycle_status"), r.getString("status"),
                        "SUBMITTED".equals(r.getString("lifecycle_status")) ? "SUBMITTED_SNAPSHOT" : "REOPENED_SAVED_RECORD",
                        r.getBigDecimal("opening_cash"), r.getBigDecimal("expected_closing_cash"),
                        r.getBigDecimal("actual_closing_cash"), r.getBigDecimal("variance"),
                        r.getObject("submitted_at", LocalDateTime.class), r.getObject("reopened_at", LocalDateTime.class)));
    }
}
