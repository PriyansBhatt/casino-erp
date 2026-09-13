package com.casino.casinoerp;

import com.casino.casinoerp.repository.CustomerSessionRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.jpa.repository.Query;

import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Executes the repository aggregate against SELECT-only fixtures, never persisted rows or migrations. */
class CustomerVisitSummaryQueryTests {
    @ParameterizedTest
    @CsvSource({
            "OPEN,false,2026-09-02,true",
            "OPEN,true,2026-09-02,false",
            "CLOSED,false,2026-09-02,false",
            "OPEN,false,2000-01-01,true"
    })
    void currentlyInsideRequiresOpenAndUnexitedRegardlessOfBusinessDate(
            String status, boolean exited, LocalDate date, boolean expectedInside) throws Exception {
        var properties = new Properties();
        try (var input = getClass().getResourceAsStream("/application.properties")) {
            properties.load(input);
        }
        String aggregate = CustomerSessionRepository.class
                .getMethod("findVisitSummariesByCustomerIds", Collection.class)
                .getAnnotation(Query.class).value()
                .replace("session.customer_sessions", "fixture")
                .replace(":customerIds", "?");
        String sql = """
                with fixture(id, customer_id, business_date, entry_time, created_at, status, exit_time) as (
                    values (?::uuid, ?::uuid, ?::date, timestamp '2026-09-04 17:27:29',
                            timestamp '2026-09-04 17:27:29', ?::text, ?::timestamp)
                )
                """ + aggregate;
        UUID customerId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(
                properties.getProperty("spring.datasource.url"),
                properties.getProperty("spring.datasource.username"),
                properties.getProperty("spring.datasource.password"))) {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement(sql)) {
                statement.setObject(1, sessionId);
                statement.setObject(2, customerId);
                statement.setObject(3, date);
                statement.setString(4, status);
                statement.setTimestamp(5, exited ? java.sql.Timestamp.valueOf("2026-09-04 18:00:00") : null);
                statement.setObject(6, customerId);
                try (var result = statement.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getBoolean("hasActiveSession")).isEqualTo(expectedInside);
                    assertThat(result.getObject("activeSessionId"))
                            .isEqualTo(expectedInside ? sessionId : null);
                    assertThat(result.getLong("totalVisits")).isEqualTo(1);
                    assertThat(result.getDate("lastVisitBusinessDate").toLocalDate()).isEqualTo(date);
                    assertThat(result.next()).isFalse();
                }
            } finally {
                connection.rollback();
            }
        }
    }
}
