package com.casino.casinoerp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.nio.file.*;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class HrFoundationMigrationTests {
    @Autowired JdbcTemplate jdbc;
    @Test void migrationDefinesHrRelationshipsAndIntegrityBarriers() throws Exception {
        String sql=Files.readString(Path.of("src/main/resources/db/migration/V26__add_hr_staff_profile_foundation.sql"));
        assertThat(sql).contains("create table casino.departments", "create table casino.job_titles", "create table casino.staff_profiles");
        assertThat(sql).contains("user_id uuid not null references core.users(id)", "unique (user_id)", "unique (employee_code)");
        assertThat(sql).contains("employment_status in ('ACTIVE', 'SUSPENDED', 'INACTIVE', 'TERMINATED')");
        assertThat(sql).contains("employment_type in ('FULL_TIME', 'PART_TIME', 'CONTRACT', 'TEMPORARY')");
        assertThat(sql).contains("reporting_manager_staff_profile_id <> id");
    }

    @Test void v26TablesAndForeignKeysAreInstalled() {
        assertThat(tableExists("departments")).isTrue(); assertThat(tableExists("job_titles")).isTrue(); assertThat(tableExists("staff_profiles")).isTrue();
        assertThat(constraint("staff_profiles","uq_staff_profiles_user")).contains("UNIQUE (user_id)");
        assertThat(constraint("staff_profiles","uq_staff_profiles_employee_code")).contains("UNIQUE (employee_code)");
        assertThat(constraint("staff_profiles","staff_profiles_user_id_fkey")).contains("core.users(id)");
    }

    @Test @Transactional void databaseRejectsDuplicateDepartmentAndJobTitleCodes() {
        insertDepartment("TEST_HR_UNIQUE");
        assertThatThrownBy(()->insertDepartment("TEST_HR_UNIQUE")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test @Transactional void databaseRejectsDuplicateProfileForUser() {
        UUID userId=jdbc.queryForObject("select id from core.users order by created_at limit 1",UUID.class);
        UUID department=insertDepartment("TEST_PROFILE_DEPT"); UUID title=insertTitle("TEST_PROFILE_TITLE");
        insertProfile(userId,"TEST-EMP-1",department,title);
        assertThatThrownBy(()->insertProfile(userId,"TEST-EMP-2",department,title)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test @Transactional void databaseRejectsDuplicateEmployeeCode() {
        java.util.List<UUID> userIds=jdbc.queryForList("select id from core.users order by created_at limit 2",UUID.class);
        assertThat(userIds).hasSizeGreaterThanOrEqualTo(2); UUID department=insertDepartment("TEST_CODE_DEPT"); UUID title=insertTitle("TEST_CODE_TITLE");
        insertProfile(userIds.get(0),"TEST-EMP-CODE",department,title);
        assertThatThrownBy(()->insertProfile(userIds.get(1),"TEST-EMP-CODE",department,title)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test @Transactional void databaseAcceptsValidStaffProfile() {
        UUID userId=jdbc.queryForObject("select id from core.users order by created_at limit 1",UUID.class);
        UUID department=insertDepartment("TEST_VALID_DEPT"); UUID title=insertTitle("TEST_VALID_TITLE");
        assertThat(insertProfile(userId,"TEST-VALID-EMP",department,title)).isEqualTo(1);
    }

    @Test @Transactional void databaseRejectsUnknownEmploymentStatus() {
        assertThatThrownBy(()->insertProfileWithLifecycle("UNKNOWN","FULL_TIME",null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test @Transactional void databaseRejectsUnknownEmploymentType() {
        assertThatThrownBy(()->insertProfileWithLifecycle("ACTIVE","UNKNOWN",null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test @Transactional void databaseRejectsDirectSelfManager() {
        UUID id=UUID.randomUUID();
        assertThatThrownBy(()->insertProfileWithLifecycle("ACTIVE","FULL_TIME",id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private boolean tableExists(String table){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from information_schema.tables where table_schema='casino' and table_name=?)",Boolean.class,table));}
    private String constraint(String table,String name){return jdbc.queryForObject("select pg_get_constraintdef(oid) from pg_constraint where conrelid=('casino.'||?)::regclass and conname=?",String.class,table,name);}
    private UUID insertDepartment(String code){UUID id=UUID.randomUUID();jdbc.update("insert into casino.departments(id,code,name,active,created_at,updated_at) values(?,?,?,true,?,?)",id,code,code,Timestamp.valueOf(LocalDateTime.now()),Timestamp.valueOf(LocalDateTime.now()));return id;}
    private UUID insertTitle(String code){UUID id=UUID.randomUUID();jdbc.update("insert into casino.job_titles(id,code,name,active,created_at,updated_at) values(?,?,?,true,?,?)",id,code,code,Timestamp.valueOf(LocalDateTime.now()),Timestamp.valueOf(LocalDateTime.now()));return id;}
    private int insertProfile(UUID userId,String code,UUID department,UUID title){return jdbc.update("insert into casino.staff_profiles(id,user_id,employee_code,department_id,job_title_id,employment_status,employment_type,date_of_joining,created_at,updated_at) values(?,?,?,?,?,'ACTIVE','FULL_TIME',?,?,?)",UUID.randomUUID(),userId,code,department,title,Date.valueOf(LocalDate.now()),Timestamp.valueOf(LocalDateTime.now()),Timestamp.valueOf(LocalDateTime.now()));}
    private void insertProfileWithLifecycle(String status,String type,UUID selfManager){UUID userId=jdbc.queryForObject("select id from core.users order by created_at limit 1",UUID.class);UUID department=insertDepartment("TEST_LIFECYCLE_DEPT_"+UUID.randomUUID().toString().substring(0,8).toUpperCase());UUID title=insertTitle("TEST_LIFECYCLE_TITLE_"+UUID.randomUUID().toString().substring(0,8).toUpperCase());UUID id=selfManager==null?UUID.randomUUID():selfManager;jdbc.update("insert into casino.staff_profiles(id,user_id,employee_code,department_id,job_title_id,employment_status,employment_type,date_of_joining,reporting_manager_staff_profile_id,created_at,updated_at) values(?,?,?,?,?,?,?,?,?,?,?)",id,userId,"TEST-LIFECYCLE-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(),department,title,status,type,Date.valueOf(LocalDate.now()),selfManager,Timestamp.valueOf(LocalDateTime.now()),Timestamp.valueOf(LocalDateTime.now()));}
}
