package com.casino.casinoerp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class BusinessDateInvariantMigrationTests {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void v24ConstraintsAndSingleOpenIndexAreInstalled() {
        assertThat(nullable("business_date")).isFalse();
        assertThat(nullable("status")).isFalse();
        assertThat(nullable("opened_at")).isFalse();
        assertThat(constraintDefinition("ck_business_dates_status"))
                .contains("status", "OPEN", "CLOSED");
        assertThat(constraintDefinition("ck_business_dates_lifecycle"))
                .contains("closed_at", "OPEN", "CLOSED");
        assertThat(indexDefinition("uq_business_dates_single_open"))
                .contains("UNIQUE", "status", "WHERE", "OPEN");
    }

    @Test
    @Transactional
    void databaseRejectsSecondOpenBusinessDate() {
        jdbc.update("update casino.business_dates set status='CLOSED', "
                + "closed_at=coalesce(closed_at, opened_at) where status='OPEN'");
        insert(LocalDate.of(2099, 1, 1), "OPEN", null);

        assertThatThrownBy(() -> insert(LocalDate.of(2099, 1, 2), "OPEN", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void databaseAllowsOneOpenAndMultipleClosedDates() {
        jdbc.update("update casino.business_dates set status='CLOSED', "
                + "closed_at=coalesce(closed_at, opened_at) where status='OPEN'");

        assertThat(insert(LocalDate.of(2099, 2, 1), "OPEN", null)).isEqualTo(1);
        assertThat(insert(LocalDate.of(2099, 2, 2), "CLOSED", LocalDateTime.of(2099, 2, 3, 1, 0)))
                .isEqualTo(1);
        assertThat(insert(LocalDate.of(2099, 2, 3), "CLOSED", LocalDateTime.of(2099, 2, 4, 1, 0)))
                .isEqualTo(1);
    }

    @Test
    @Transactional
    void databaseRejectsClosedDateWithoutClosedTimestamp() {
        assertInvalid(() -> insert(LocalDate.of(2099, 3, 1), "CLOSED", null));
    }

    @Test
    @Transactional
    void databaseRejectsOpenDateWithClosedTimestamp() {
        assertInvalid(() -> insert(LocalDate.of(2099, 3, 2), "OPEN",
                LocalDateTime.of(2099, 3, 3, 1, 0)));
    }

    @Test
    @Transactional
    void databaseRejectsInvalidStatus() {
        assertInvalid(() -> insert(LocalDate.of(2099, 3, 3), "PENDING", null));
    }

    @Test
    @Transactional
    void databaseRejectsNullBusinessDate() {
        assertInvalid(() -> insert(null, "OPEN", null));
    }

    @Test
    @Transactional
    void databaseRejectsNullStatus() {
        assertInvalid(() -> insert(LocalDate.of(2099, 3, 4), null, null));
    }

    @Test
    @Transactional
    void databaseRejectsNullOpenedTimestamp() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into casino.business_dates
                    (id, business_date, status, opened_at, closed_at)
                values (?, ?, 'OPEN', null, null)
                """, UUID.randomUUID(), LocalDate.of(2099, 3, 5)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private int insert(LocalDate date, String status, LocalDateTime closedAt) {
        return jdbc.update("""
                insert into casino.business_dates
                    (id, business_date, status, opened_at, closed_at)
                values (?, ?, ?, ?, ?)
                """, UUID.randomUUID(), date, status,
                Timestamp.valueOf(LocalDateTime.of(2099, 1, 1, 12, 0)),
                closedAt == null ? null : Timestamp.valueOf(closedAt));
    }

    private void assertInvalid(Runnable insert) {
        assertThatThrownBy(insert::run).isInstanceOf(DataIntegrityViolationException.class);
    }

    private boolean nullable(String column) {
        return "YES".equals(jdbc.queryForObject("""
                select is_nullable from information_schema.columns
                where table_schema='casino' and table_name='business_dates' and column_name=?
                """, String.class, column));
    }

    private String constraintDefinition(String name) {
        return jdbc.queryForObject("""
                select pg_get_constraintdef(oid) from pg_constraint
                where conrelid='casino.business_dates'::regclass and conname=?
                """, String.class, name);
    }

    private String indexDefinition(String name) {
        return jdbc.queryForObject("""
                select indexdef from pg_indexes
                where schemaname='casino' and tablename='business_dates' and indexname=?
                """, String.class, name);
    }
}
