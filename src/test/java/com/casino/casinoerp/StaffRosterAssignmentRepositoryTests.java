package com.casino.casinoerp;

import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.repository.StaffRosterAssignmentRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.sql.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class StaffRosterAssignmentRepositoryTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired StaffRosterAssignmentRepository rosters;
    private Fixture first, second;
    private final LocalDate date = LocalDate.of(2098, 9, 1);

    @BeforeEach void setup() {
        first = fixture("FILTER_A");
        second = fixture("FILTER_B");
        insertRoster(first.staff(), first.shift(), date.minusDays(1), "SCHEDULED", null);
        insertRoster(first.staff(), first.shift(), date, "SCHEDULED", null);
        insertRoster(first.staff(), first.shift(), date.plusDays(1), "CANCELLED", "test");
        insertRoster(second.staff(), second.shift(), date.plusDays(2), "SCHEDULED", null);
    }

    @Test void noFilters() {
        assertThat(search(null, null, null, null, null, null)).hasSize(4)
                .extracting(StaffRosterAssignment::getRosterDate)
                .containsExactly(date.minusDays(1), date, date.plusDays(1), date.plusDays(2));
    }
    @Test void fromDateOnlyIsInclusive() {
        assertThat(search(date, null, null, null, null, null)).extracting(StaffRosterAssignment::getRosterDate)
                .containsExactly(date, date.plusDays(1), date.plusDays(2));
    }
    @Test void toDateOnlyIsInclusive() {
        assertThat(search(null, date, null, null, null, null)).extracting(StaffRosterAssignment::getRosterDate)
                .containsExactly(date.minusDays(1), date);
    }
    @Test void bothDatesAreInclusive() {
        assertThat(search(date, date.plusDays(1), null, null, null, null))
                .extracting(StaffRosterAssignment::getRosterDate).containsExactly(date, date.plusDays(1));
    }
    @Test void status() {
        assertThat(search(null, null, null, null, null, RosterStatus.CANCELLED)).hasSize(1);
        assertThat(search(null, null, null, null, null, RosterStatus.SCHEDULED)).hasSize(3);
    }
    @Test void staff() {
        assertThat(search(null, null, second.staff(), null, null, null)).hasSize(1);
        assertThat(search(null, null, UUID.randomUUID(), null, null, null)).isEmpty();
    }
    @Test void department() {
        assertThat(search(null, null, null, departmentId(), null, null)).hasSize(3);
        assertThat(search(null, null, null, UUID.randomUUID(), null, null)).isEmpty();
    }
    @Test void shift() {
        assertThat(search(null, null, null, null, second.shift(), null)).hasSize(1);
        assertThat(search(null, null, null, null, UUID.randomUUID(), null)).isEmpty();
    }
    @Test void combinedFilters() {
        assertThat(search(date, date, first.staff(), departmentId(), first.shift(), RosterStatus.SCHEDULED)).hasSize(1);
        assertThat(search(date, date, first.staff(), departmentId(), second.shift(), RosterStatus.SCHEDULED)).isEmpty();
    }
    private UUID departmentId() {
        return jdbc.queryForObject("select department_id from casino.staff_profiles where id=?", UUID.class, first.staff());
    }
    private List<StaffRosterAssignment> search(LocalDate from, LocalDate to, UUID staff, UUID department,
            UUID shift, RosterStatus status) {
        // Execute against PostgreSQL with exactly the requested filters; isolate assertions from existing data.
        return rosters.search(from, to, staff, department, shift, status).stream()
                .filter(r -> Set.of(first.staff(), second.staff()).contains(r.getStaffProfileId())).toList();
    }
    private UUID insertShift(String code,LocalTime start,LocalTime end,boolean crosses,int late,int early){UUID id=UUID.randomUUID();jdbc.update("insert into casino.shift_definitions(id,code,name,start_time,end_time,crosses_midnight,late_grace_minutes,early_check_in_minutes,active,created_at,updated_at) values(?,?,?,?,?,?,?,?,true,?,?)",id,code,code,Time.valueOf(start),Time.valueOf(end),crosses,late,early,Timestamp.valueOf(LocalDateTime.now()),Timestamp.valueOf(LocalDateTime.now()));return id;}
    private Fixture fixture(String suffix){UUID department=UUID.randomUUID(),title=UUID.randomUUID(),staff=UUID.randomUUID();UUID user=UUID.randomUUID();jdbc.update("insert into core.users(id,username,password_hash,full_name,email,status,role,created_at) values(?,?,?,?,?,'ACTIVE','DEALER',?)",user,"roster-test-"+user,"unused","Roster Test",user+"@example.test",Timestamp.valueOf(LocalDateTime.now()));Timestamp now=Timestamp.valueOf(LocalDateTime.now());jdbc.update("insert into casino.departments(id,code,name,active,created_at,updated_at) values(?,?,?,true,?,?)",department,"TEST_ROSTER_DEPT_"+suffix,"Department",now,now);jdbc.update("insert into casino.job_titles(id,code,name,active,created_at,updated_at) values(?,?,?,true,?,?)",title,"TEST_ROSTER_TITLE_"+suffix,"Title",now,now);jdbc.update("insert into casino.staff_profiles(id,user_id,employee_code,department_id,job_title_id,employment_status,employment_type,date_of_joining,created_at,updated_at) values(?,?,?,?,?,'ACTIVE','FULL_TIME',?,?,?)",staff,user,"TEST-ROSTER-"+suffix,department,title,java.sql.Date.valueOf(LocalDate.now()),now,now);return new Fixture(staff,insertShift("TEST_SHIFT_"+suffix,LocalTime.of(18,0),LocalTime.of(3,30),true,0,0));}
    private int insertRoster(UUID staff,UUID shift,LocalDate date,String status,String reason){Timestamp now=Timestamp.valueOf(LocalDateTime.now());UUID actor=jdbc.queryForObject("select id from core.users order by created_at limit 1",UUID.class);return jdbc.update("insert into casino.staff_roster_assignments(id,staff_profile_id,shift_definition_id,roster_date,status,cancellation_reason,cancelled_by,cancelled_at,created_at,updated_at) values(?,?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),staff,shift,java.sql.Date.valueOf(date),status,reason,"CANCELLED".equals(status)?actor:null,"CANCELLED".equals(status)?now:null,now,now);}private record Fixture(UUID staff,UUID shift){}
}
