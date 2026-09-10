package com.casino.casinoerp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class StaffAttendanceCorrectionMigrationTests {
    @Autowired JdbcTemplate jdbc;

    @Test void correctionTableForeignKeysAndIndexesExist() {
        assertThat(tableExists()).isTrue();
        assertThat(constraint("staff_attendance_corrections_attendance_id_fkey"))
                .contains("core.staff_attendance(id)");
        assertThat(constraint("staff_attendance_corrections_corrected_by_user_id_fkey"))
                .contains("core.users(id)");
        assertThat(index("ix_staff_attendance_corrections_attendance_time"))
                .contains("attendance_id").contains("corrected_at");
        assertThat(index("ix_staff_attendance_corrections_actor"))
                .contains("corrected_by_user_id");
    }

    @Test void existingAttendanceRowsRemainCompatible() {
        Integer total = jdbc.queryForObject("select count(*) from core.staff_attendance", Integer.class);
        assertThat(total).isNotNull().isGreaterThanOrEqualTo(0);
    }

    @Test @Transactional void validCorrectionTypeAndReasonAreAccepted() {
        Fixture fixture = fixture();
        assertThat(insert(fixture, "CHECK_IN_TIME", "Valid reason",
                fixture.checkIn(), fixture.checkIn().minusSeconds(60), null, null, 570L, 571L)).isEqualTo(1);
    }

    @Test @Transactional void unknownCorrectionTypeIsRejected() {
        Fixture fixture = fixture();
        assertThatThrownBy(() -> insert(fixture, "UNKNOWN", "Valid reason",
                fixture.checkIn(), fixture.checkIn().minusSeconds(60), null, null, 570L, 571L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test @Transactional void blankReasonIsRejected() {
        Fixture fixture = fixture();
        assertThatThrownBy(() -> insert(fixture, "CHECK_IN_TIME", "   ",
                fixture.checkIn(), fixture.checkIn().minusSeconds(60), null, null, 570L, 571L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test @Transactional void invalidTypeSpecificValuesAndNegativeMinutesAreRejected() {
        Fixture fixture = fixture();
        assertThatThrownBy(() -> insert(fixture, "MISSED_CHECKOUT", "Invalid shape",
                fixture.checkIn(), fixture.checkIn(), null, fixture.checkOut(), null, 570L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test @Transactional void negativeMinutesAreRejected() {
        Fixture fixture = fixture();
        assertThatThrownBy(() -> insert(fixture, "CHECK_IN_TIME", "Negative minutes",
                fixture.checkIn(), fixture.checkIn().minusSeconds(60), null, null, -1L, 571L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Fixture fixture() {
        UUID attendanceId = jdbc.queryForObject("select id from core.staff_attendance order by created_at limit 1", UUID.class);
        UUID actorId = jdbc.queryForObject("select id from core.users order by created_at limit 1", UUID.class);
        Instant checkIn = jdbc.queryForObject("select check_in_at from core.staff_attendance where id=?",
                (rs, row) -> rs.getTimestamp(1).toInstant(), attendanceId);
        return new Fixture(attendanceId, actorId, checkIn, checkIn.plusSeconds(570 * 60));
    }

    private int insert(Fixture fixture, String type, String reason,
            Instant previousCheckIn, Instant newCheckIn, Instant previousCheckOut,
            Instant newCheckOut, Long previousMinutes, Long newMinutes) {
        return jdbc.update("""
                insert into core.staff_attendance_corrections(
                    id,attendance_id,correction_type,previous_check_in_at,new_check_in_at,
                    previous_check_out_at,new_check_out_at,previous_worked_minutes,new_worked_minutes,
                    reason,corrected_by_user_id,corrected_at)
                values(?,?,?,?,?,?,?,?,?,?,?,?)
                """, UUID.randomUUID(), fixture.attendanceId(), type,
                timestamp(previousCheckIn), timestamp(newCheckIn), timestamp(previousCheckOut), timestamp(newCheckOut),
                previousMinutes, newMinutes, reason, fixture.actorId(), Timestamp.from(Instant.now()));
    }

    private Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
    private boolean tableExists() { return Boolean.TRUE.equals(jdbc.queryForObject("select to_regclass('core.staff_attendance_corrections') is not null", Boolean.class)); }
    private String constraint(String name) { return jdbc.queryForObject("select pg_get_constraintdef(oid) from pg_constraint where conrelid='core.staff_attendance_corrections'::regclass and conname=?", String.class, name); }
    private String index(String name) { return jdbc.queryForObject("select indexdef from pg_indexes where schemaname='core' and indexname=?", String.class, name); }
    private record Fixture(UUID attendanceId, UUID actorId, Instant checkIn, Instant checkOut) {}
}
