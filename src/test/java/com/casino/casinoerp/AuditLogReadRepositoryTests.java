package com.casino.casinoerp;
import com.casino.casinoerp.repository.AuditLogReadRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** PostgreSQL SELECT-only CTE fixtures. Never reads or modifies operational tables. */
class AuditLogReadRepositoryTests {
 private static final String FIXTURES="""
 with fixture_users(id,username,full_name,status) as (
 values ('00000000-0000-0000-0000-000000000001'::uuid,'active','Active Name','ACTIVE'),
 ('00000000-0000-0000-0000-000000000002'::uuid,'inactive','Inactive Name','INACTIVE')),
 fixture_audit as (
 select ('00000000-0000-0000-0000-'||lpad(n::text,12,'0'))::uuid id,
 date '2026-09-02' business_date,
 case when n=3 then 'SYSTEM_UNLOCK' when n=4 then 'FUTURE_EVENT' when n=7 then 'USER_ROLE_CHANGED' when n=8 then 'USER_ACTIVATED' when n=9 then 'USER_DEACTIVATED' else 'USER_CREATED' end action_type,
 'USER_MANAGEMENT'::text module_name,
 '10000000-0000-0000-0000-000000000001'::uuid entity_id,
 case when n=5 then null::uuid when n=6 then '00000000-0000-0000-0000-000000000099'::uuid
 when n=2 then '00000000-0000-0000-0000-000000000002'::uuid else '00000000-0000-0000-0000-000000000001'::uuid end performed_by,
 case when n=1 then null::timestamp else timestamp '2026-09-03 16:00:00' end performed_at,
 case when n in (3,4,7,8,9) then 'WITHHELD_SECRET' else 'role=CASHIER; status=ACTIVE' end remarks
 from generate_series(1,106) n)
 """;
 @Test void realPostgresBoundsFiltersActorsAndSafeDetails()throws Exception {
  String url=System.getenv("AU1_TEST_JDBC_URL");
  if(url==null || !url.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):5432/casino_erp_(a1_test|st1[a-z0-9_]*)"))throw new IllegalStateException("Explicit isolated AU1_TEST_JDBC_URL required.");
  try(var connection=DriverManager.getConnection(url,System.getenv("AU1_TEST_DB_USER"),System.getenv("AU1_TEST_DB_PASSWORD"))) {
   connection.setReadOnly(true);connection.setAutoCommit(false);
   int[] queries={0};
   var jdbc=new NamedParameterJdbcTemplate(new SingleConnectionDataSource(connection,true)) {
    @Override public <T> List<T> query(String sql,SqlParameterSource args,RowMapper<T> mapper) {
     queries[0]++;assertThat(sql).doesNotContain("count(").contains("limit :limit offset :offset");
     return super.query(FIXTURES+sql.replace("audit.audit_logs","fixture_audit").replace("core.users","fixture_users"),args,mapper);
    }
   };
   var repo=new AuditLogReadRepository(jdbc);
   var first=repo.read(0,100,null,null,null,null,null,null,null);
   assertThat(queries[0]).isEqualTo(1);assertThat(first.items()).hasSize(100);assertThat(first.hasNext()).isTrue();
   assertThat(first.items().getFirst().id().toString()).endsWith("106");
   var last=repo.read(1,100,null,null,null,null,null,null,null);
   assertThat(last.items()).hasSize(6);assertThat(last.hasNext()).isFalse();assertThat(last.items().getLast().performedAt()).isNull();
   var rows=new ArrayList<>(first.items());rows.addAll(last.items());
   assertThat(rows).extracting(r->r.id()).doesNotHaveDuplicates();
   for(int n=0;n<rows.size();n++)assertThat(rows.get(n).id().toString()).endsWith(String.format("%012d",106-n));
   assertThat(repo.read(Integer.MAX_VALUE,100,null,null,null,null,null,null,null).items()).isEmpty();
   assertThat(rows.stream().filter(r->r.id().toString().endsWith("002")).findFirst().orElseThrow().actor().username()).isEqualTo("inactive");
   assertThat(rows.stream().filter(r->r.id().toString().endsWith("005")).findFirst().orElseThrow().actor()).isNull();
   var missing=rows.stream().filter(r->r.id().toString().endsWith("006")).findFirst().orElseThrow().actor();assertThat(missing.id()).isNotNull();assertThat(missing.username()).isNull();
   assertThat(rows.stream().filter(r->r.detailsWithheld()).toList()).hasSize(5).allMatch(r->r.safeDetails()==null);
   assertThat(first.items().getFirst().safeDetails()).contains("role=CASHIER");
   var from=LocalDateTime.of(2026,9,3,0,0);var actor=UUID.fromString("00000000-0000-0000-0000-000000000002");
   assertThat(repo.read(0,50,from,from.plusDays(1),LocalDate.of(2026,9,2),"USER_CREATED","USER_MANAGEMENT",actor,"Inactive Name").items()).hasSize(1);
   assertThat(repo.read(0,50,null,from,null,null,null,null,null).items()).isEmpty();
   assertThat(repo.read(0,50,null,null,LocalDate.of(2026,9,3),null,null,null,null).items()).isEmpty();
   for(String search:List.of("WITHHELD_SECRET","%","_%","role=CASHIER"))assertThat(repo.read(0,50,null,null,null,null,null,null,search).items()).isEmpty();
   assertThat(repo.read(3,50,null,null,null,null,null,null,null).items()).isEmpty();
   String json=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().writeValueAsString(rows);
   assertThat(json).doesNotContain("WITHHELD_SECRET","passwordHash","remarks","jwt","historicalRole");
   connection.rollback();
  }
 }
}
