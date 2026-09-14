package com.casino.casinoerp;

import com.casino.casinoerp.repository.CashierReconciliationReadRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Production aggregate SQL evaluated against SELECT-only CTE fixtures, never operational writes. */
class CashierReconciliationReadRepositoryTests {
    private static final String FIXTURES = """
        with bi(created_by,business_date,payment_mode,amount_received) as (
          values(md5('cashier')::uuid,date '2026-09-02','CASH',1000::numeric),
                (md5('cashier')::uuid,date '2026-09-02','CASH',500::numeric),
                (md5('cashier')::uuid,date '2026-09-02','BANK',9000::numeric),
                (md5('cashier')::uuid,date '2026-09-02','CARD',8000::numeric),
                (md5('cashier')::uuid,date '2026-09-02','QR',7000::numeric),
                (md5('other')::uuid,date '2026-09-02','CASH',99999::numeric),
                (md5('cashier')::uuid,date '2026-09-01','CASH',99999::numeric)
        ), co(created_by,business_date,payment_mode,cash_paid) as (
          values(md5('cashier')::uuid,date '2026-09-02','CASH',300::numeric),
                (md5('cashier')::uuid,date '2026-09-02','QR',4000::numeric)
        ), lr(created_by,business_date,payment_mode,amount_paid) as (
          values(md5('cashier')::uuid,date '2026-09-02','CASH',200::numeric)
        )
        """;
    private static String fixtures(String sql) {
        return FIXTURES + sql.replace("cashier.chip_buy_ins", "bi")
                .replace("cashier.chip_cash_outs", "co").replace("cashier.losing_returns", "lr");
    }
    @Test void groupedTotalsCountsAndExistenceRespectActorAndPersistedDate() throws Exception {
        var properties = new Properties();
        try (var input = getClass().getResourceAsStream("/application.properties")) { properties.load(input); }
        try (var connection = DriverManager.getConnection(properties.getProperty("spring.datasource.url"),
                properties.getProperty("spring.datasource.username"), properties.getProperty("spring.datasource.password"))) {
            connection.setReadOnly(true); connection.setAutoCommit(false);
            var jdbc = new NamedParameterJdbcTemplate(new SingleConnectionDataSource(connection, true)) {
                @Override public <T> List<T> query(String sql, Map<String, ?> parameters, RowMapper<T> mapper) {
                    return super.query(fixtures(sql), parameters, mapper);
                }
                @Override public <T> T queryForObject(String sql, Map<String, ?> parameters, Class<T> type) {
                    return super.queryForObject(fixtures(sql), parameters, type);
                }
            };
            try {
                var repo = new CashierReconciliationReadRepository(jdbc);
                UUID actor = UUID.nameUUIDFromBytes("unused".getBytes());
                // The DB fixture uses MD5 UUIDs, independently resolved by SELECT.
                try (var st = connection.createStatement(); var rs = st.executeQuery("select md5('cashier')::uuid")) { rs.next(); actor = rs.getObject(1, UUID.class); }
                var date = LocalDate.of(2026,9,2);
                var rows = repo.tenders(actor, date);
                assertThat(rows).hasSize(7);
                assertThat(rows).filteredOn(r -> r.source().equals("BUY_IN") && r.paymentMode().equals("CASH"))
                        .singleElement().satisfies(r -> { assertThat(r.count()).isEqualTo(2); assertThat(r.amount()).isEqualByComparingTo("1500"); });
                assertThat(rows).filteredOn(r -> r.source().equals("LOSING_RETURN")).singleElement()
                        .satisfies(r -> assertThat(r.amount()).isEqualByComparingTo("200"));
                assertThat(repo.hasActivity(actor,date)).isTrue();
                assertThat(repo.hasActivity(actor,date.plusDays(1))).isFalse();
                assertThat(repo.tenders(UUID.randomUUID(),date)).isEmpty();
            } finally { connection.rollback(); }
        }
    }
}
