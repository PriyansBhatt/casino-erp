package com.casino.casinoerp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import com.casino.casinoerp.entity.LeaveRequestStatus;
import com.casino.casinoerp.repository.StaffLeaveRequestRepository;

import java.sql.*;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class StaffLeaveMigrationTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired StaffLeaveRequestRepository requests;

    @Test void tablesForeignKeysAndIndexesExist() {
        assertThat(table("leave_types")).isTrue();
        assertThat(table("staff_leave_requests")).isTrue();
        assertThat(constraint("staff_leave_requests", "staff_leave_requests_staff_profile_id_fkey"))
                .contains("casino.staff_profiles(id)");
        assertThat(constraint("staff_leave_requests", "staff_leave_requests_leave_type_id_fkey"))
                .contains("casino.leave_types(id)");
        assertThat(index("ix_staff_leave_requests_staff_dates")).contains("staff_profile_id");
        assertThat(index("ix_staff_leave_requests_status_dates")).contains("status");
        assertThat(index("ix_staff_leave_requests_leave_type")).contains("leave_type_id");
    }

    @Test @Transactional void canonicalAndUniqueLeaveTypeCodeIsEnforced() {
        insertType("TEST_ANNUAL", "Annual", true);
        assertThatThrownBy(() -> insertType("TEST_ANNUAL", "Duplicate", true))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test @Transactional void nonCanonicalCodeAndBlankNameAreRejected() {
        assertThatThrownBy(() -> insertType("lowercase", "Annual", true))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test @Transactional void blankLeaveTypeNameIsRejected() {
        assertThatThrownBy(() -> insertType("TEST_BLANK_NAME", "   ", true))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test @Transactional void validPendingFullDayRequestIsAccepted() {
        Fixture fixture = fixture();
        assertThat(insertRequest(fixture.staff(), fixture.type(), "PENDING",
                LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 16), "Family leave")).isEqualTo(1);
    }

    @Test @Transactional void invalidStatusIsRejected() {
        Fixture fixture = fixture();
        assertThatThrownBy(() -> insertRequest(fixture.staff(), fixture.type(), "UNKNOWN",
                LocalDate.now(), LocalDate.now(), "Invalid status"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test @Transactional void reversedDatesAreRejected() {
        Fixture fixture = fixture();
        assertThatThrownBy(() -> insertRequest(fixture.staff(), fixture.type(), "PENDING",
                LocalDate.of(2026, 9, 16), LocalDate.of(2026, 9, 14), "Invalid dates"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test @Transactional void blankAndOversizedReasonsAreRejected() {
        Fixture fixture = fixture();
        assertThatThrownBy(() -> insertRequest(fixture.staff(), fixture.type(), "PENDING",
                LocalDate.now(), LocalDate.now(), "   "))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test @Transactional void oversizedReasonIsRejected() {
        Fixture fixture = fixture();
        assertThatThrownBy(() -> insertRequest(fixture.staff(), fixture.type(), "PENDING",
                LocalDate.now(), LocalDate.now(), "x".repeat(1001)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test @Transactional void unknownStaffAndLeaveTypeForeignKeysAreRejected() {
        Fixture fixture = fixture();
        assertThatThrownBy(() -> insertRequest(UUID.randomUUID(), fixture.type(), "PENDING",
                LocalDate.now(), LocalDate.now(), "Unknown staff"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test @Transactional void unknownLeaveTypeForeignKeyIsRejected() {
        Fixture fixture = fixture();
        assertThatThrownBy(() -> insertRequest(fixture.staff(), UUID.randomUUID(), "PENDING",
                LocalDate.now(), LocalDate.now(), "Unknown Leave Type"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void existingHrAndAttendanceDataRemainCompatible() {
        assertThat(jdbc.queryForObject("select count(*) from casino.staff_profiles", Integer.class)).isNotNull();
        assertThat(jdbc.queryForObject("select count(*) from core.staff_attendance", Integer.class)).isNotNull();
    }

    @Test @Transactional void overlapQueryUsesInclusiveBoundariesAndBlockingStatuses() {
        Fixture fixture = fixture();
        insertRequest(fixture.staff(), fixture.type(), "PENDING",
                LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 12), "Pending leave");
        insertRequest(fixture.staff(), fixture.type(), "REJECTED",
                LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 22), "Rejected leave");
        List<LeaveRequestStatus> blocking = List.of(
                LeaveRequestStatus.PENDING, LeaveRequestStatus.APPROVED);

        assertThat(requests.findOverlapping(fixture.staff(), blocking,
                LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 13))).hasSize(1);
        assertThat(requests.findOverlapping(fixture.staff(), blocking,
                LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 14))).isEmpty();
        assertThat(requests.findOverlapping(fixture.staff(), blocking,
                LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10))).hasSize(1);
        assertThat(requests.findOverlapping(fixture.staff(), blocking,
                LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 22))).isEmpty();
    }

    private Fixture fixture() {
        UUID user = jdbc.queryForObject("select id from core.users order by created_at limit 1", UUID.class);
        UUID department = UUID.randomUUID();
        UUID title = UUID.randomUUID();
        UUID staff = UUID.randomUUID();
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbc.update("insert into casino.departments(id,code,name,active,created_at,updated_at) values(?,?,?,true,?,?)",
                department, code("DEPT"), "Test Department", now, now);
        jdbc.update("insert into casino.job_titles(id,code,name,active,created_at,updated_at) values(?,?,?,true,?,?)",
                title, code("TITLE"), "Test Title", now, now);
        jdbc.update("insert into casino.staff_profiles(id,user_id,employee_code,department_id,job_title_id,employment_status,employment_type,date_of_joining,created_at,updated_at) values(?,?,?,?,?,'ACTIVE','FULL_TIME',?,?,?)",
                staff, user, code("EMP"), department, title, java.sql.Date.valueOf(LocalDate.now()), now, now);
        return new Fixture(staff, insertType(code("TYPE"), "Test Leave", true));
    }

    private UUID insertType(String code, String name, boolean active) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbc.update("insert into casino.leave_types(id,code,name,active,created_at,updated_at) values(?,?,?,?,?,?)",
                id, code, name, active, now, now);
        return id;
    }

    private int insertRequest(UUID staff, UUID type, String status,
            LocalDate start, LocalDate end, String reason) {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        return jdbc.update("insert into casino.staff_leave_requests(id,staff_profile_id,leave_type_id,start_date,end_date,reason,status,submitted_at,created_at,updated_at) values(?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), staff, type, java.sql.Date.valueOf(start), java.sql.Date.valueOf(end), reason, status, now, now, now);
    }

    private boolean table(String name) { return Boolean.TRUE.equals(jdbc.queryForObject("select to_regclass('casino.' || ?) is not null", Boolean.class, name)); }
    private String constraint(String table, String name) { return jdbc.queryForObject("select pg_get_constraintdef(oid) from pg_constraint where conrelid=('casino.' || ?)::regclass and conname=?", String.class, table, name); }
    private String index(String name) { return jdbc.queryForObject("select indexdef from pg_indexes where schemaname='casino' and indexname=?", String.class, name); }
    private String code(String prefix) { return prefix + "_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT); }
    private record Fixture(UUID staff, UUID type) {}
}
