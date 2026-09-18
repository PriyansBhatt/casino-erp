package com.casino.casinoerp;

import com.casino.casinoerp.repository.ManagementReportReadRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** SELECT-only PostgreSQL CTE fixtures: no operational table reads, DDL or writes. */
class ManagementReportReadRepositoryTests {
    private static final String FIXTURES="""
        with fixture_sessions(business_date,customer_id,entry_time,status,exit_time) as (
        values(date '2026-09-02',1,timestamp '2026-09-04 17:27:29','OPEN',null::timestamp),
        (date '2026-09-02',1,timestamp '2026-09-03 08:59:00','CLOSED',null::timestamp),
        (date '2026-09-02',2,timestamp '2026-09-02 09:00:00','CLOSED',timestamp '2026-09-03 01:00:00'),
        (date '2026-09-03',3,timestamp '2026-09-03 09:00:00','OPEN',null::timestamp)),
        fixture_buyins(business_date,payment_mode,amount_received) as (
        values(date '2026-09-02','CASH',100.25),(date '2026-09-02','CASH',50.25),
        (date '2026-09-02','BANK',200.50),(date '2026-09-02','CARD',300.75),(date '2026-09-02','QR',400.00),
        (date '2026-09-03','CASH',9999.00)),
        fixture_cashouts(business_date,payment_mode,cash_paid) as (
        values(date '2026-09-02','CASH',10.00),(date '2026-09-02','BANK',20.00),
        (date '2026-09-02','CARD',30.00),(date '2026-09-02','QR',40.00)),
        fixture_returns(business_date,amount_paid) as (
        values(date '2026-09-02',123.45),(date '2026-09-02',10.00),(date '2026-09-03',999.00)),
        fixture_gaming(business_date,result_type,amount) as (
        values(date '2026-09-02','WIN',80.00),(date '2026-09-02','LOSS',120.00),(date '2026-09-03','LOSS',999.00)),
        fixture_users(id,username,full_name,status) as (
        values('00000000-0000-0000-0000-000000000001'::uuid,'cashier','Current label','INACTIVE')),
        fixture_reconciliations as (
        select ('10000000-0000-0000-0000-'||lpad(n::text,12,'0'))::uuid id,
        case when n=106 then date '2026-09-03' else date '2026-09-02' end business_date,
        case when n=104 then '00000000-0000-0000-0000-000000000099'::uuid
             else '00000000-0000-0000-0000-000000000001'::uuid end cashier_user_id,
        case when n=105 then 'REOPENED' else 'SUBMITTED' end lifecycle_status,
        'SHORT'::text status,1000.00 opening_cash,1050.00 expected_closing_cash,1040.00 actual_closing_cash,
        case when n=105 then 999.00 else -10.00 end variance,
        timestamp '2026-09-03 08:00:00' submitted_at,
        case when n=105 then timestamp '2026-09-03 08:30:00' else null::timestamp end reopened_at
        from generate_series(1,106) n)
        """;
    @Test void aggregatesPersistedDatesTendersSnapshotsAndBoundedRows() throws Exception {
        String url=System.getenv("RP1_TEST_JDBC_URL");
        if(url==null || !url.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):5432/casino_erp_(a1_test|st1[a-z0-9_]*)"))
            throw new IllegalStateException("Explicit isolated RP1_TEST_JDBC_URL required.");
        try(var connection=DriverManager.getConnection(url,System.getenv("RP1_TEST_DB_USER"),System.getenv("RP1_TEST_DB_PASSWORD"))){
            connection.setReadOnly(true);connection.setAutoCommit(false);
            int[] calls={0};
            String[] corrupted={null};
            var jdbc=new NamedParameterJdbcTemplate(new SingleConnectionDataSource(connection,true)){
                String fixture(String sql){calls[0]++;return (corrupted[0]==null ? FIXTURES : corrupted[0])+sql.replace("session.customer_sessions","fixture_sessions")
                    .replace("cashier.chip_buy_ins","fixture_buyins").replace("cashier.chip_cash_outs","fixture_cashouts")
                    .replace("cashier.losing_returns","fixture_returns").replace("casino.verified_gaming_results","fixture_gaming")
                    .replace("cashier.cashier_reconciliations","fixture_reconciliations").replace("core.users","fixture_users");}
                @Override public <T>T queryForObject(String sql,Map<String,?> args,RowMapper<T> mapper){return super.queryForObject(fixture(sql),args,mapper);}
                @Override public void query(String sql,Map<String,?> args,RowCallbackHandler handler){super.query(fixture(sql),args,handler);}
                @Override public <T>List<T> query(String sql,Map<String,?> args,RowMapper<T> mapper){return super.query(fixture(sql),args,mapper);}
            };
            var repo=new ManagementReportReadRepository(jdbc);var date=LocalDate.of(2026,9,2);
            assertThat(repo.guests(date)).isEqualTo(new ManagementReportReadRepository.Guests(3,2));
            var payments=repo.payments(date);assertThat(payments.buyIn().get("CASH").count()).isEqualTo(2);
            assertThat(payments.buyIn().get("CASH").amount()).isEqualByComparingTo("150.50");
            assertThat(payments.buyIn().get("BANK").amount()).isEqualByComparingTo("200.50");
            assertThat(payments.buyIn().get("CARD").amount()).isEqualByComparingTo("300.75");
            assertThat(payments.buyIn().get("QR").amount()).isEqualByComparingTo("400");
            int multiplier=1;
            for(String mode:List.of("CASH","BANK","CARD","QR")) {
                assertThat(payments.cashOut().get(mode).count()).isEqualTo(1);
                assertThat(payments.cashOut().get(mode).amount()).isEqualByComparingTo(String.valueOf(10*multiplier++));
            }
            assertThat(repo.payouts(date).amount()).isEqualByComparingTo("133.45");
            var gaming=repo.gaming(date);assertThat(gaming.wins()).isEqualByComparingTo("80");assertThat(gaming.losses()).isEqualByComparingTo("120");
            var summary=repo.reconciliations(date);assertThat(summary.submitted()).isEqualTo(104);assertThat(summary.reopened()).isOne();assertThat(summary.variance()).isEqualByComparingTo("-1040");
            assertThat(calls[0]).isEqualTo(5);
            var first=repo.rows(date,0,100);assertThat(first).hasSize(101);assertThat(calls[0]).isEqualTo(6);
            assertThat(first.getFirst().id().toString()).endsWith("105");assertThat(first.getFirst().calculationBasis()).isEqualTo("REOPENED_SAVED_RECORD");assertThat(first.getFirst().actualClosingCash()).isNull();assertThat(first.getFirst().variance()).isNull();assertThat(first.getFirst().status()).isNull();
            assertThat(first.get(1).cashierName()).isNull();assertThat(first.get(2).cashierName()).isEqualTo("Current label");
            var last=repo.rows(date,1,100);assertThat(last).hasSize(5);assertThat(last.getLast().id().toString()).endsWith("001");
            var all=new ArrayList<>(first.subList(0,100));all.addAll(last);
            assertThat(all).hasSize(105).extracting(r->r.id()).doesNotHaveDuplicates();
            for(int n=0;n<all.size();n++)assertThat(all.get(n).id().toString()).endsWith(String.format("%012d",105-n));
            assertThat(repo.rows(date,Integer.MAX_VALUE,100)).isEmpty();
            var empty=date.plusDays(2);assertThat(repo.guests(empty).entries()).isZero();assertThat(repo.reconciliations(empty).variance()).isNull();
            assertThat(repo.payments(empty).buyIn().values()).allMatch(v->v.count()==0&&v.amount().signum()==0);
            assertThat(repo.payouts(empty).amount()).isZero();assertThat(repo.gaming(empty).wins()).isZero();
            String json=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().writeValueAsString(first);
            assertThat(json).doesNotContain("remarks","password","cashReceived","cashPaid","paymentMode","phone","email","denominations");
            corrupted[0]=FIXTURES.replace("'QR',400.00", "'FUTURE',400.00");
            assertThatThrownBy(()->repo.payments(date)).isInstanceOf(IllegalStateException.class);
            corrupted[0]=FIXTURES.replace("'CASH',50.25", "'CASH',null::numeric");
            assertThatThrownBy(()->repo.payments(date)).isInstanceOf(IllegalStateException.class);
            connection.rollback();
        }
    }
}
