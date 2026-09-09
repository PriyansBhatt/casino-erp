package com.casino.casinoerp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.*;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class StaffAttendanceRosterMigrationTests {
    @Autowired JdbcTemplate jdbc;

    @Test void nullableRosterLinkAndSnapshotColumnsAreInstalled() {
        assertThat(nullable("roster_assignment_id")).isTrue();
        assertThat(nullable("scheduled_start_at")).isTrue();
        assertThat(nullable("scheduled_end_at")).isTrue();
        assertThat(constraint("staff_attendance_roster_assignment_id_fkey"))
                .contains("FOREIGN KEY (roster_assignment_id)")
                .contains("casino.staff_roster_assignments(id)");
        assertThat(constraint("chk_staff_attendance_roster_snapshot"))
                .contains("scheduled_end_at > scheduled_start_at")
                .contains("roster_late_grace_minutes >= 0")
                .contains("roster_early_check_in_minutes >= 0");
        assertThat(index("ix_staff_attendance_roster_assignment"))
                .contains("roster_assignment_id")
                .contains("WHERE (roster_assignment_id IS NOT NULL)");
    }

    @Test void existingAttendanceRowsRemainValidWithoutRosterData() {
        Integer invalid = jdbc.queryForObject("""
                select count(*) from core.staff_attendance
                where roster_assignment_id is null and (
                    roster_date is not null or shift_code is not null or shift_name is not null
                    or scheduled_start_at is not null or scheduled_end_at is not null
                    or roster_late_grace_minutes is not null or roster_early_check_in_minutes is not null)
                """, Integer.class);
        assertThat(invalid).isZero();
    }

    @Test @Transactional void allNullSnapshotAndCompleteSnapshotAreAccepted() {
        Fixture fixture = fixture("COMPLETE");
        assertThat(insertAttendance(fixture.userId(), null, null, null, null, null, null, null, null)).isEqualTo(1);
        assertThat(insertAttendance(fixture.userId(), fixture.rosterId(), fixture.rosterDate(), "NIGHT", "Night Shift",
                instant(2026, 9, 10, 18, 0), instant(2026, 9, 11, 3, 30), 10, 30)).isEqualTo(1);
    }

    @Test @Transactional void partialSnapshotIsRejected() {
        UUID userId = availableUser();
        assertThatThrownBy(() -> insertAttendance(userId, null, null, "NIGHT", null, null, null, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test @Transactional void negativeSnapshotGraceIsRejected() {
        Fixture fixture = fixture("NEGATIVE");
        assertThatThrownBy(() -> insertAttendance(fixture.userId(), fixture.rosterId(), fixture.rosterDate(), "NIGHT", "Night Shift",
                instant(2026, 9, 10, 18, 0), instant(2026, 9, 11, 3, 30), -1, 30))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test @Transactional void invalidSnapshotIntervalIsRejected() {
        Fixture fixture = fixture("INTERVAL");
        assertThatThrownBy(() -> insertAttendance(fixture.userId(), fixture.rosterId(), fixture.rosterDate(), "NIGHT", "Night Shift",
                instant(2026, 9, 11, 3, 30), instant(2026, 9, 10, 18, 0), 10, 30))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private boolean nullable(String column) {
        return "YES".equals(jdbc.queryForObject("""
                select is_nullable from information_schema.columns
                where table_schema='core' and table_name='staff_attendance' and column_name=?
                """, String.class, column));
    }

    private String constraint(String name) {
        return jdbc.queryForObject("""
                select pg_get_constraintdef(oid) from pg_constraint
                where conrelid='core.staff_attendance'::regclass and conname=?
                """, String.class, name);
    }

    private String index(String name) {
        return jdbc.queryForObject("select indexdef from pg_indexes where schemaname='core' and indexname=?", String.class, name);
    }

    private Fixture fixture(String suffix) {
        UUID userId = availableUser();
        UUID departmentId = UUID.randomUUID(), titleId = UUID.randomUUID(), staffId = UUID.randomUUID();
        UUID shiftId = UUID.randomUUID(), rosterId = UUID.randomUUID();
        LocalDate rosterDate = LocalDate.of(2098, 9, 10);
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbc.update("insert into casino.departments(id,code,name,active,created_at,updated_at) values(?,?,?,true,?,?)",
                departmentId, "PHASE_C_DEPT_" + suffix, "Phase C Department", now, now);
        jdbc.update("insert into casino.job_titles(id,code,name,active,created_at,updated_at) values(?,?,?,true,?,?)",
                titleId, "PHASE_C_TITLE_" + suffix, "Phase C Title", now, now);
        jdbc.update("insert into casino.staff_profiles(id,user_id,employee_code,department_id,job_title_id,employment_status,employment_type,date_of_joining,created_at,updated_at) values(?,?,?,?,?,'ACTIVE','FULL_TIME',?,?,?)",
                staffId, userId, "PHASE-C-" + suffix, departmentId, titleId, java.sql.Date.valueOf(LocalDate.now()), now, now);
        jdbc.update("insert into casino.shift_definitions(id,code,name,start_time,end_time,crosses_midnight,late_grace_minutes,early_check_in_minutes,active,created_at,updated_at) values(?,?,?,'18:00','03:30',true,10,30,true,?,?)",
                shiftId, "PHASE_C_SHIFT_" + suffix, "Night Shift", now, now);
        jdbc.update("insert into casino.staff_roster_assignments(id,staff_profile_id,shift_definition_id,roster_date,status,created_at,updated_at) values(?,?,?,?,'SCHEDULED',?,?)",
                rosterId, staffId, shiftId, java.sql.Date.valueOf(rosterDate), now, now);
        return new Fixture(userId, rosterId, rosterDate);
    }

    private UUID availableUser() {
        return jdbc.queryForObject("""
                select users.id from core.users users
                where not exists (select 1 from casino.staff_profiles staff where staff.user_id=users.id)
                order by users.created_at limit 1
                """, UUID.class);
    }

    private int insertAttendance(UUID userId, UUID rosterId, LocalDate rosterDate, String shiftCode,
            String shiftName, Instant scheduledStart, Instant scheduledEnd, Integer lateGrace, Integer earlyGrace) {
        Instant checkIn = instant(2098, 9, 10, 18, 0);
        Instant checkOut = checkIn.plusSeconds(60);
        return jdbc.update("""
                insert into core.staff_attendance(
                    id,user_id,business_date,status,check_in_at,check_out_at,created_at,updated_at,
                    roster_assignment_id,roster_date,shift_code,shift_name,scheduled_start_at,scheduled_end_at,
                    roster_late_grace_minutes,roster_early_check_in_minutes)
                values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, UUID.randomUUID(), userId, java.sql.Date.valueOf(LocalDate.of(2098, 9, 10)),
                "CLOSED", Timestamp.from(checkIn), Timestamp.from(checkOut), Timestamp.from(checkIn), Timestamp.from(checkOut),
                rosterId, rosterDate == null ? null : java.sql.Date.valueOf(rosterDate), shiftCode, shiftName,
                scheduledStart == null ? null : Timestamp.from(scheduledStart),
                scheduledEnd == null ? null : Timestamp.from(scheduledEnd), lateGrace, earlyGrace);
    }

    private Instant instant(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZoneId.of("Asia/Kathmandu")).toInstant();
    }

    private record Fixture(UUID userId, UUID rosterId, LocalDate rosterDate) {}
}
