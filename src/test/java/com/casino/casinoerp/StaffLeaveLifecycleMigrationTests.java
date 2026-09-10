package com.casino.casinoerp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import com.casino.casinoerp.entity.LeaveRequestStatus;
import com.casino.casinoerp.repository.StaffLeaveRequestRepository;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class StaffLeaveLifecycleMigrationTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired StaffLeaveRequestRepository requests;

    @Test void lifecycleColumnsForeignKeysAndIndexesExist() {
        assertThat(column("reviewed_by_user_id")).isTrue();
        assertThat(column("reviewed_at")).isTrue();
        assertThat(column("review_reason")).isTrue();
        assertThat(column("cancelled_by_user_id")).isTrue();
        assertThat(column("cancelled_at")).isTrue();
        assertThat(column("cancellation_reason")).isTrue();
        assertThat(constraint("staff_leave_requests_reviewed_by_user_id_fkey")).contains("core.users(id)");
        assertThat(constraint("staff_leave_requests_cancelled_by_user_id_fkey")).contains("core.users(id)");
        assertThat(index("ix_staff_leave_requests_reviewed_by")).contains("reviewed_by_user_id");
        assertThat(index("ix_staff_leave_requests_cancelled_by")).contains("cancelled_by_user_id");
    }

    @Test @Transactional void validLifecycleStatesAreAccepted() {
        Fixture f = fixture();
        assertThat(insert(f, "PENDING", null, null, null, null, null, null)).isEqualTo(1);
        assertThat(insert(f, "APPROVED", f.actor(), now(), "Approved", null, null, null)).isEqualTo(1);
        assertThat(insert(f, "REJECTED", f.actor(), now(), "Denied", null, null, null)).isEqualTo(1);
        assertThat(insert(f, "CANCELLED", null, null, null, f.actor(), now(), "Withdrawn")).isEqualTo(1);
        assertThat(insert(f, "CANCELLED", f.actor(), now(), "Approved",
                f.actor(), now(), "Coverage changed")).isEqualTo(1);
    }

    @Test @Transactional void rejectedRequiresReviewerAndTimestamp() {
        Fixture f = fixture();
        assertInvalid(() -> insert(f, "REJECTED", null, null, null, null, null, null));
    }

    @Test @Transactional void rejectedRequiresReason() {
        Fixture f = fixture();
        assertInvalid(() -> insert(f, "REJECTED", f.actor(), now(), null, null, null, null));
    }

    @Test @Transactional void cancelledRequiresActorAndTimestamp() {
        Fixture f = fixture();
        assertInvalid(() -> insert(f, "CANCELLED", null, null, null, null, null, null));
    }

    @Test @Transactional void cancelledRequiresReason() {
        Fixture f = fixture();
        assertInvalid(() -> insert(f, "CANCELLED", null, null, null, f.actor(), now(), null));
    }

    @Test @Transactional void pendingRejectsReviewMetadata() {
        Fixture f = fixture();
        assertInvalid(() -> insert(f, "PENDING", f.actor(), now(), null, null, null, null));
    }

    @Test @Transactional void approvedRequiresReviewMetadata() {
        Fixture f = fixture();
        assertInvalid(() -> insert(f, "APPROVED", null, null, null, null, null, null));
    }

    @Test @Transactional void approvedRejectsCancellationMetadata() {
        Fixture f = fixture();
        assertInvalid(() -> insert(f, "APPROVED", f.actor(), now(), null,
                f.actor(), now(), "Invalid simultaneous cancellation"));
    }

    @Test void existingE1RowsRemainCompatible() {
        Integer invalid = jdbc.queryForObject("""
                select count(*) from casino.staff_leave_requests
                where status = 'PENDING' and (reviewed_by_user_id is not null or reviewed_at is not null
                  or review_reason is not null or cancelled_by_user_id is not null
                  or cancelled_at is not null or cancellation_reason is not null)
                """, Integer.class);
        assertThat(invalid).isZero();
    }

    @Test @Transactional void lifecycleStatusesNaturallyControlOverlapBlocking() {
        Fixture f = fixture();
        insert(f, "APPROVED", f.actor(), now(), "Approved", null, null, null);
        insert(f, "REJECTED", f.actor(), now(), "Denied", null, null, null);
        insert(f, "CANCELLED", null, null, null, f.actor(), now(), "Withdrawn");
        assertThat(requests.findOverlapping(f.staff(),
                List.of(LeaveRequestStatus.PENDING, LeaveRequestStatus.APPROVED),
                LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 21))).hasSize(1);
    }

    private Fixture fixture() {
        UUID actor = jdbc.queryForObject("select id from core.users order by created_at limit 1", UUID.class);
        UUID department = UUID.randomUUID(), title = UUID.randomUUID(), staff = UUID.randomUUID(), type = UUID.randomUUID();
        Timestamp now = now();
        jdbc.update("insert into casino.departments(id,code,name,active,created_at,updated_at) values(?,?,?,true,?,?)",
                department, code("DEPT"), "Lifecycle Department", now, now);
        jdbc.update("insert into casino.job_titles(id,code,name,active,created_at,updated_at) values(?,?,?,true,?,?)",
                title, code("TITLE"), "Lifecycle Title", now, now);
        jdbc.update("insert into casino.staff_profiles(id,user_id,employee_code,department_id,job_title_id,employment_status,employment_type,date_of_joining,created_at,updated_at) values(?,?,?,?,?,'ACTIVE','FULL_TIME',?,?,?)",
                staff, actor, code("EMP"), department, title, java.sql.Date.valueOf(LocalDate.now()), now, now);
        jdbc.update("insert into casino.leave_types(id,code,name,active,created_at,updated_at) values(?,?,?,true,?,?)",
                type, code("TYPE"), "Lifecycle Leave", now, now);
        return new Fixture(staff, type, actor);
    }

    private int insert(Fixture f, String status, UUID reviewer, Timestamp reviewedAt, String reviewReason,
            UUID canceller, Timestamp cancelledAt, String cancellationReason) {
        Timestamp now = now();
        return jdbc.update("""
                insert into casino.staff_leave_requests(
                  id,staff_profile_id,leave_type_id,start_date,end_date,reason,status,submitted_at,
                  reviewed_by_user_id,reviewed_at,review_reason,cancelled_by_user_id,cancelled_at,cancellation_reason,
                  created_at,updated_at) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, UUID.randomUUID(), f.staff(), f.type(), java.sql.Date.valueOf(LocalDate.of(2026, 9, 20)),
                java.sql.Date.valueOf(LocalDate.of(2026, 9, 21)), "Lifecycle test", status, now,
                reviewer, reviewedAt, reviewReason, canceller, cancelledAt, cancellationReason, now, now);
    }

    private void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(DataIntegrityViolationException.class);
    }
    private boolean column(String name) { return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from information_schema.columns where table_schema='casino' and table_name='staff_leave_requests' and column_name=?)", Boolean.class, name)); }
    private String constraint(String name) { return jdbc.queryForObject("select pg_get_constraintdef(oid) from pg_constraint where conrelid='casino.staff_leave_requests'::regclass and conname=?", String.class, name); }
    private String index(String name) { return jdbc.queryForObject("select indexdef from pg_indexes where schemaname='casino' and indexname=?", String.class, name); }
    private Timestamp now() { return Timestamp.valueOf(LocalDateTime.now()); }
    private String code(String prefix) { return prefix + "_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT); }
    private record Fixture(UUID staff, UUID type, UUID actor) {}
}
