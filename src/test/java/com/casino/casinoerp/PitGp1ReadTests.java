package com.casino.casinoerp;

import com.casino.casinoerp.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** SELECT-only fixtures on a read-only connection. No operational rows or schemas are changed. */
class PitGp1ReadTests {
    static final UUID TABLE=UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final String FIXTURES="""
        with test_tables(id) as (values('00000000-0000-0000-0000-000000000001'::uuid),('00000000-0000-0000-0000-000000000002'::uuid)),
        test_assignments(pit_table_id,status,customer_session_id,id) as (
          values('00000000-0000-0000-0000-000000000001'::uuid,'ACTIVE',md5('s1')::uuid,md5('a1')::uuid),
                ('00000000-0000-0000-0000-000000000001'::uuid,'LEFT',md5('s2')::uuid,md5('a2')::uuid)),
        test_movements(pit_table_id,movement_type,total_value) as (
          values('00000000-0000-0000-0000-000000000001'::uuid,'CUSTOMER_TO_TABLE',500::numeric),
                ('00000000-0000-0000-0000-000000000001'::uuid,'CUSTOMER_TO_TABLE',1000::numeric),
                ('00000000-0000-0000-0000-000000000001'::uuid,'TABLE_FLOAT_ISSUE',99999::numeric)),
        test_results(pit_table_id,assignment_id,result_type,amount) as (
          values('00000000-0000-0000-0000-000000000001'::uuid,md5('a2')::uuid,'WIN',500::numeric),
                ('00000000-0000-0000-0000-000000000001'::uuid,md5('a2')::uuid,'LOSS',1000::numeric)),
        test_customers(id,customer_code,full_name,status) as (
          values(md5('c1')::uuid,'CUS-1','Inside','ACTIVE'),(md5('c2')::uuid,'CUS-2','Exited','ACTIVE'),(md5('c3')::uuid,'CUS-3','Old date','ACTIVE')),
        test_sessions(id,customer_id,session_code,status,business_date,exit_time) as (
          values(md5('s1')::uuid,md5('c1')::uuid,'SES-1','OPEN',date '2026-09-02',null::timestamp),
                (md5('s2')::uuid,md5('c2')::uuid,'SES-2','OPEN',date '2026-09-02',timestamp '2026-09-02 15:00'),
                (md5('s3')::uuid,md5('c3')::uuid,'SES-3','OPEN',date '2026-09-01',null::timestamp))
        """;
    static String replace(String sql) {
        return sql.replace("casino.pit_tables","test_tables")
                .replace("casino.pit_table_customer_assignments","test_assignments")
                .replace("cashier.chip_custody_movements","test_movements")
                .replace("casino.verified_gaming_results","test_results")
                .replace("customer.customers","test_customers").replace("session.customer_sessions","test_sessions");
    }
    @Test void aggregatesAndEligibleSearchUseAuthoritativeOperationAndUnexitedDateScope() throws Exception {
        Properties p=new Properties();try(var in=getClass().getResourceAsStream("/application.properties")){p.load(in);}
        try(var connection=DriverManager.getConnection(p.getProperty("spring.datasource.url"),p.getProperty("spring.datasource.username"),p.getProperty("spring.datasource.password"))){
            connection.setReadOnly(true);connection.setAutoCommit(false);
            try {
                var jdbc=new NamedParameterJdbcTemplate(new SingleConnectionDataSource(connection,true)) {
                    @Override public void query(String sql,Map<String,?> params,RowCallbackHandler handler) {
                        super.query(FIXTURES+", "+replace(sql).stripLeading().replaceFirst("with ",""),params,handler);
                    }
                };
                var repo=new PitReadRepository(jdbc);
                var totals=repo.summarize(List.of(TABLE));
                assertThat(totals).hasSize(1);
                assertThat(totals.get(TABLE).players()).isEqualTo(1);
                assertThat(totals.get(TABLE).chipIn()).isEqualByComparingTo("1500");
                assertThat(totals.get(TABLE).wins()).isEqualByComparingTo("500");
                assertThat(totals.get(TABLE).losses()).isEqualByComparingTo("1000");
                assertThat(repo.summarize(List.of())).isEmpty();
                var empty=repo.summarize(List.of(UUID.fromString("00000000-0000-0000-0000-000000000002"))).values().iterator().next();
                assertThat(empty.players()).isZero();assertThat(empty.wins()).isZero();assertThat(empty.chipIn()).isZero();
                var query=CustomerRepository.class.getMethod("searchOpenSessionCandidates",LocalDate.class,String.class).getAnnotation(Query.class).value();
                var rows=jdbc.queryForList(FIXTURES+replace(query),Map.of("businessDate",LocalDate.of(2026,9,2),"query","CUS"));
                assertThat(rows).hasSize(1);assertThat(rows.getFirst().get("customerCode")).isEqualTo("CUS-1");
            }finally{connection.rollback();}
        }
    }
}
