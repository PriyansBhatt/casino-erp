package com.casino.casinoerp;

import com.casino.casinoerp.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.jpa.repository.Query;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/** Executes production queries against SELECT-only CTE fixtures; no application/Flyway startup. */
class I1aReadBoundaryTests {
    static Connection connection() throws Exception {
        var properties = new Properties();
        try (var input = I1aReadBoundaryTests.class.getResourceAsStream("/application.properties")) {
            properties.load(input);
        }
        var c = DriverManager.getConnection(properties.getProperty("spring.datasource.url"),
                properties.getProperty("spring.datasource.username"), properties.getProperty("spring.datasource.password"));
        c.setReadOnly(true);
        c.setAutoCommit(false);
        return c;
    }

    static UUID id(int value) {
        return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(value));
    }

    private static final String CASH_FIXTURES = """
            bi(created_by,business_date,payment_mode,amount_received) as (
                values ('00000000-0000-0000-0000-000000000001'::uuid,date '2026-09-02','CASH',100::numeric),
                       ('00000000-0000-0000-0000-000000000002'::uuid,date '2026-09-02','CASH',200),
                       ('00000000-0000-0000-0000-000000000002'::uuid,date '2026-09-02','CASH',300),
                       ('00000000-0000-0000-0000-000000000003'::uuid,date '2026-09-02','BANK',400),
                       ('00000000-0000-0000-0000-000000000003'::uuid,date '2026-09-02','CARD',500),
                       ('00000000-0000-0000-0000-000000000003'::uuid,date '2026-09-02','QR',600),
                       ('00000000-0000-0000-0000-000000000004'::uuid,date '2026-09-01','CASH',700),
                       ('00000000-0000-0000-0000-000000000009'::uuid,date '2026-09-02','CASH',900),
                       ('00000000-0000-0000-0000-000000000010'::uuid,date '2026-09-02','CASH',1000)
            ), co(created_by,business_date,payment_mode,cash_paid) as (
                values ('00000000-0000-0000-0000-000000000005'::uuid,date '2026-09-02','CASH',50::numeric),
                       ('00000000-0000-0000-0000-000000000003'::uuid,date '2026-09-02','BANK',1000)
            ), lr(created_by,business_date,payment_mode,amount_paid) as (
                values ('00000000-0000-0000-0000-000000000006'::uuid,date '2026-09-02','CASH',60::numeric)
            ), openings(id,cashier_user_id,business_date) as (
                values (gen_random_uuid(),'00000000-0000-0000-0000-000000000007'::uuid,date '2026-09-02')
            ), reconciliations(cashier_user_id,business_date,lifecycle_status) as (
                values ('00000000-0000-0000-0000-000000000007'::uuid,date '2026-09-02','SUBMITTED'),
                       ('00000000-0000-0000-0000-000000000008'::uuid,date '2026-09-02','REOPENED')
            ), resolutions(actor_user_id,business_date,cash_received,cash_paid,net_cash_movement) as (
                values ('00000000-0000-0000-0000-000000000009'::uuid,date '2026-09-02',900::numeric,0::numeric,900::numeric),
                       ('00000000-0000-0000-0000-000000000010'::uuid,date '2026-09-02',999::numeric,0::numeric,999::numeric)
            ),
            """;

    @Test
    void cashSourcesDiscoverUniqueActorsWithoutAccountStatusOrNoncashModules() throws Exception {
        try (var c = connection()) {
            var jdbc = new NamedParameterJdbcTemplate(new SingleConnectionDataSource(c, true)) {
                @Override public <T> List<T> query(String sql, Map<String, ?> args, RowMapper<T> mapper) {
                    assertThat(sql).doesNotContain("core.users", "slot_plays", "fnb", "customer_service_records",
                            "chip_custody", "verified_gaming_results");
                    String fixture = sql.replaceFirst("with ", "with " + CASH_FIXTURES)
                            .replace("cashier.chip_buy_ins", "bi").replace("cashier.chip_cash_outs", "co")
                            .replace("cashier.losing_returns", "lr").replace("cashier.cashier_opening_balances", "openings")
                            .replace("cashier.cashier_reconciliations", "reconciliations")
                            .replace("cashier.legacy_cash_actor_resolutions", "resolutions");
                    return super.query(fixture, args, mapper);
                }
            };
            try {
                var rows = new BusinessDateCloseReadRepository(jdbc).actors(LocalDate.of(2026,9,2));
                assertThat(rows).extracting(BusinessDateCloseReadRepository.Actor::id)
                        .containsExactlyInAnyOrder(id(1),id(2),id(5),id(6),id(7),id(8),id(9),id(10));
                assertThat(rows).filteredOn(a -> a.id().equals(id(7))).singleElement()
                        .satisfies(a -> { assertThat(a.hasOpening()).isTrue(); assertThat(a.reconciliationStatus()).isEqualTo("SUBMITTED"); });
                assertThat(rows).filteredOn(a -> a.id().equals(id(9))).singleElement().satisfies(a -> assertThat(a.legacyResolved()).isTrue());
                assertThat(rows).filteredOn(a -> a.id().equals(id(10))).singleElement().satisfies(a -> assertThat(a.legacyResolved()).isFalse());
                assertThat(new BusinessDateCloseReadRepository(jdbc).actors(LocalDate.of(2026,9,3))).isEmpty();
                // Exercise discovery and day-close validation together. Current users are deliberately
                // not consulted: active, inactive, missing and role-changed accounts have identical obligations.
                var validator = new com.casino.casinoerp.service.BusinessDateValidationService(
                        org.mockito.Mockito.mock(CustomerSessionRepository.class),
                        org.mockito.Mockito.mock(PitTableCustomerAssignmentRepository.class),
                        org.mockito.Mockito.mock(com.casino.casinoerp.service.SessionFinancialPositionService.class),
                        org.mockito.Mockito.mock(PitTableRepository.class),
                        org.mockito.Mockito.mock(PitTableStaffAssignmentRepository.class),
                        org.mockito.Mockito.mock(ChipCustodyInventoryRepository.class),
                        new BusinessDateCloseReadRepository(jdbc));
                assertThat(validator.validateCloseRequirements(LocalDate.of(2026,9,2)))
                        .anyMatch(error -> error.startsWith("5 cash activity/opening-balance actor(s)"))
                        .anyMatch(error -> error.startsWith("1 cashier reconciliation(s) remain REOPENED"));
                assertThat(validator.validateCloseRequirements(LocalDate.of(2026,9,3))).isEmpty();
            } finally { c.rollback(); }
        }
    }

    private static final String DEPENDENCIES = """
            with inventory(location_key,location_type,reference_id,quantity) as (
                values ('CUSTOMER_SESSION:00000000-0000-0000-0000-000000000001','CUSTOMER_SESSION','00000000-0000-0000-0000-000000000001'::uuid,1::bigint),
                       ('CUSTOMER_SESSION:00000000-0000-0000-0000-000000000002','CUSTOMER_SESSION','00000000-0000-0000-0000-000000000002'::uuid,0),
                       ('PIT_TABLE:00000000-0000-0000-0000-000000000003','PIT_TABLE','00000000-0000-0000-0000-000000000003'::uuid,999)
            ), plays(customer_session_id,status) as (
                values ('00000000-0000-0000-0000-000000000001'::uuid,'ACTIVE'),
                       ('00000000-0000-0000-0000-000000000002'::uuid,'ENDED')
            )
            """;

    @ParameterizedTest
    @CsvSource({"1,true,true", "2,false,false", "3,false,false", "4,false,false"})
    void exactSessionDependenciesIncludeOneChipButExcludeTablesEndedAndOtherSessions(
            int session, boolean chips, boolean active) throws Exception {
        try (var c = connection()) {
            var jdbc = new NamedParameterJdbcTemplate(new SingleConnectionDataSource(c, true)) {
                @Override public <T> T queryForObject(String sql, Map<String, ?> args, Class<T> type) {
                    return super.queryForObject(DEPENDENCIES + sql.replace("cashier.chip_custody_inventory", "inventory")
                            .replace("casino.slot_plays", "plays"), args, type);
                }
            };
            try {
                String sql = ChipCustodyInventoryRepository.class.getMethod("hasCustomerSessionChips", UUID.class)
                        .getAnnotation(Query.class).value();
                assertThat(jdbc.queryForObject(sql, Map.of("sessionId",id(session)), Boolean.class)).isEqualTo(chips);
                assertThat(new MachineRepository(jdbc).hasActivePlay(id(session))).isEqualTo(active);
            } finally { c.rollback(); }
        }
    }
}
