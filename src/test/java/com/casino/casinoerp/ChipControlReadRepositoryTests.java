package com.casino.casinoerp;

import com.casino.casinoerp.repository.ChipControlReadRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Actual PostgreSQL SQL and row mapping, with SELECT-only fixtures. No schema/data writes. */
class ChipControlReadRepositoryTests {
    private static final LocalDate DATE = LocalDate.of(2026, 9, 2);
    private static final String FIXTURES = """
        with sessions as (
          select md5(n::text)::uuid id, md5(('c' || n)::text)::uuid customer_id, 'SES-' || n session_code,
                 case when n = 9 then date '2026-09-01' else date '2026-09-02' end business_date,
                 timestamp '2026-09-04 17:27:29' entry_time,
                 case when n in (7,8) then 'CLOSED' else 'OPEN' end status,
                 case when n in (6,8) then timestamp '2026-09-04 18:00:00' else null end exit_time
          from generate_series(1,9) n
        ), customers as (
          select customer_id id, 'CUS-' || session_code customer_code, 'Customer' full_name from sessions
        ), bi(customer_session_id,total_chip_value_issued) as (
          values (md5('1')::uuid,1000::numeric),(md5('1')::uuid,2000::numeric),
                 (md5('4')::uuid,1000::numeric),(md5('5')::uuid,1000::numeric)
        ), co(customer_session_id,total_chip_value_returned) as (
          values (md5('1')::uuid,500::numeric),(md5('1')::uuid,1000::numeric),
                 (md5('4')::uuid,1000::numeric)
        ), gr(customer_session_id,result_type,amount) as (
          values (md5('1')::uuid,'WIN',1500::numeric),(md5('1')::uuid,'WIN',500::numeric),
                 (md5('1')::uuid,'LOSS',500::numeric),(md5('2')::uuid,'LOSS',500::numeric),
                 (md5('5')::uuid,'LOSS',1500::numeric)
        ), assignments(customer_session_id,pit_table_id,status) as (
          values (md5('5')::uuid,md5('t')::uuid,'ACTIVE'),(md5('1')::uuid,md5('t')::uuid,'LEFT')
        ), tables(id,table_code,table_name) as (values(md5('t')::uuid,'T-1','Table One')),
        users(id,username,full_name) as (values(md5('u')::uuid,'cashier','Cashier Name')),
        movement_fixture(id,customer_session_id,pit_table_id,created_by) as (
          values(md5('m')::uuid,md5('1')::uuid,md5('t')::uuid,md5('u')::uuid),
                (md5('missing')::uuid,null::uuid,null::uuid,null::uuid)
        )
        """;

    @Test void predicatesAggregatesStatusesAndBulkDisplayMappingAreAuthoritative() throws Exception {
        var properties = new Properties();
        try (var input = getClass().getResourceAsStream("/application.properties")) { properties.load(input); }
        try (var connection = DriverManager.getConnection(properties.getProperty("spring.datasource.url"),
                properties.getProperty("spring.datasource.username"), properties.getProperty("spring.datasource.password"))) {
            connection.setReadOnly(true); connection.setAutoCommit(false);
            var queryCount = new int[]{0};
            var jdbc = new NamedParameterJdbcTemplate(new SingleConnectionDataSource(connection, true)) {
                @Override public <T> List<T> query(String sql, Map<String, ?> parameters, RowMapper<T> mapper) {
                    queryCount[0]++;
                    String fixtureSql = sql.replace("session.customer_sessions", "sessions")
                            .replace("customer.customers", "customers").replace("cashier.chip_buy_ins", "bi")
                            .replace("cashier.chip_cash_outs", "co").replace("casino.verified_gaming_results", "gr")
                            .replace("casino.pit_table_customer_assignments", "assignments")
                            .replace("casino.pit_tables", "tables").replace("core.users", "users")
                            .replace("cashier.chip_custody_movements", "movement_fixture");
                    fixtureSql = FIXTURES + (fixtureSql.stripLeading().startsWith("with ")
                            ? ", " + fixtureSql.stripLeading().substring(5) : fixtureSql);
                    return super.query(fixtureSql, parameters, mapper);
                }
            };
            try {
                var repository = new ChipControlReadRepository(jdbc);
                var rows = repository.directory(DATE);
                assertThat(queryCount[0]).isEqualTo(1);
                // OPEN/exited, both CLOSED variants and wrong date all excluded.
                assertThat(rows).extracting(value -> value.sessionCode())
                        .containsExactlyInAnyOrder("SES-1", "SES-2", "SES-3", "SES-4", "SES-5");
                var byCode = rows.stream().collect(java.util.stream.Collectors.toMap(value -> value.sessionCode(), value -> value));
                var positive = byCode.get("SES-1");
                assertThat(positive.totalBuyIn()).isEqualByComparingTo("3000");
                assertThat(positive.verifiedGamingWin()).isEqualByComparingTo("2000");
                assertThat(positive.verifiedGamingLoss()).isEqualByComparingTo("500");
                assertThat(positive.totalCashOut()).isEqualByComparingTo("1500");
                assertThat(positive.calculatedChipPosition()).isEqualByComparingTo("3000");
                assertThat(positive.exposureStatus()).isEqualTo("OUTSTANDING");
                assertThat(byCode.get("SES-2").exposureStatus()).isEqualTo("NEGATIVE_POSITION");
                assertThat(byCode.get("SES-3").exposureStatus()).isEqualTo("CLEAR");
                assertThat(byCode.get("SES-4").exposureStatus()).isEqualTo("CLEAR");
                assertThat(byCode.get("SES-5").exposureStatus()).isEqualTo("AT_TABLE");
                assertThat(byCode.get("SES-5").calculatedChipPosition()).isEqualByComparingTo("-500");
                assertThat(positive.entryTime().toLocalDate()).isEqualTo(LocalDate.of(2026,9,4));
                assertThat(repository.directory(DATE.plusDays(1))).isEmpty();
                UUID movementId = UUID.fromString("6f8f5771-5090-da26-3245-3988d9a1501b"); // md5('m')
                var displays = repository.movementDisplays(List.of(movementId));
                assertThat(displays.get(movementId).customerName()).isEqualTo("Customer");
                assertThat(displays.get(movementId).sessionCode()).isEqualTo("SES-1");
                assertThat(displays.get(movementId).tableCode()).isEqualTo("T-1");
                assertThat(displays.get(movementId).actorUsername()).isEqualTo("cashier");
                int before = queryCount[0];
                assertThat(repository.movementDisplays(List.of())).isEmpty();
                assertThat(queryCount[0]).isEqualTo(before);
            } finally { connection.rollback(); }
        }
    }
}
