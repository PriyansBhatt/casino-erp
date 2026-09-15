package com.casino.casinoerp;

import com.casino.casinoerp.dto.MachineDtos.*;
import com.casino.casinoerp.repository.MachineRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** Disposable, uniquely named test schema only. Never applies migrations or writes operational tables. */
class MachineDatabaseTests {
    @Test void migrationConstraintsConcurrencyReadsAndRollback() throws Exception {
        Properties config=new Properties();try(var in=getClass().getResourceAsStream("/application.properties")){config.load(in);}
        var ds=new DriverManagerDataSource(config.getProperty("spring.datasource.url"),config.getProperty("spring.datasource.username"),config.getProperty("spring.datasource.password"));
        String schema="sm1_test_"+UUID.randomUUID().toString().replace("-","");
        UUID actor=UUID.randomUUID(),customer=UUID.randomUUID(),session=UUID.randomUUID(),other=UUID.randomUUID(),otherCustomer=UUID.randomUUID();
        LocalDate date=LocalDate.of(2026,9,15);LocalDateTime now=date.atTime(10,0);
        try(var c=ds.getConnection();var st=c.createStatement()) {
            st.execute("create schema "+schema);
            try {
                st.execute("create table "+schema+".users(id uuid primary key)");
                st.execute("create table "+schema+".customers(id uuid primary key,customer_code text,full_name text,status text)");
                st.execute("create table "+schema+".customer_sessions(id uuid primary key,customer_id uuid,session_code text,business_date date,status text,exit_time timestamp)");
                st.execute("insert into "+schema+".users values ('"+actor+"')");
                st.execute("insert into "+schema+".customers values ('"+customer+"','C1','One','ACTIVE'),('"+otherCustomer+"','C2','Two','ACTIVE')");
                st.execute("insert into "+schema+".customer_sessions values ('"+session+"','"+customer+"','S1','"+date+"','OPEN',null),('"+other+"','"+otherCustomer+"','S2','"+date+"','OPEN',null)");
                String sql;try(var in=getClass().getResourceAsStream("/db/migration/V32__add_slot_machine_foundation.sql")){sql=new String(in.readAllBytes(),StandardCharsets.UTF_8);}
                st.execute(rewrite(sql,schema));
                var jdbc=new NamedParameterJdbcTemplate(ds) {
                    @Override public <T> List<T> query(String sql,Map<String,?> args,RowMapper<T> mapper){return super.query(rewrite(sql,schema),args,mapper);}
                    @Override public int update(String sql,Map<String,?> args){return super.update(rewrite(sql,schema),args);}
                    @Override public <T>T queryForObject(String sql,Map<String,?> args,Class<T> type){return super.queryForObject(rewrite(sql,schema),args,type);}
                };
                var repo=new MachineRepository(jdbc);var tx=new TransactionTemplate(new JdbcTransactionManager(ds));
                UUID m1=UUID.randomUUID(),m2=UUID.randomUUID(),p1=UUID.randomUUID();
                repo.create(m1,new Create("s1","Slot one",Type.SLOT,"Floor",date),actor,now);
                repo.create(m2,new Create("s2","Slot two",Type.SLOT,null,date),actor,now);
                assertThatThrownBy(()->repo.create(UUID.randomUUID(),new Create("S1","Duplicate",Type.SLOT,null,date),actor,now)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
                assertThat(repo.overview()).hasSize(2).allMatch(m->m.operationalStatus().equals("AVAILABLE"));
                repo.start(p1,m1,new Start(customer,session,date,"first"),actor,date,now);
                assertThat(repo.detail(m1).orElseThrow().activePlay().customerCode()).isEqualTo("C1");
                assertThat(repo.detail(m1).orElseThrow().operationalStatus()).isEqualTo("IN_USE");
                assertThat(repo.candidates("C",date)).extracting(Candidate::customerId).containsExactly(otherCustomer);
                assertThatThrownBy(()->repo.start(UUID.randomUUID(),m2,new Start(customer,session,date,"same-session"),actor,date,now)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
                assertThatThrownBy(()->repo.start(UUID.randomUUID(),m1,new Start(otherCustomer,other,date,"same-machine"),actor,date,now)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
                var saved=repo.byKey("first",false).orElseThrow();
                repo.end(saved,new End(date,"end"),actor,now.plusMinutes(1));
                assertThat(repo.detail(m1).orElseThrow().operationalStatus()).isEqualTo("AVAILABLE");
                assertThat(repo.history(m1)).hasSize(1);
                assertThat(repo.history(m1).getFirst().endedBy()).isEqualTo(actor);
                assertThatThrownBy(()->tx.execute(status->{repo.start(UUID.randomUUID(),m1,new Start(customer,session,date,"rollback"),actor,date,now);throw new IllegalStateException("simulated audit failure");})).hasMessageContaining("audit failure");
                assertThat(repo.byKey("rollback",false)).isEmpty();
                assertThat(repo.detail(m1).orElseThrow().operationalStatus()).isEqualTo("AVAILABLE");
                // Two real connections race for one machine. The second waits, then loses to the unique index.
                try(var first=ds.getConnection();var second=ds.getConnection()) {
                    first.setAutoCommit(false);second.setAutoCommit(false);
                    String insert="insert into "+schema+".slot_plays(id,machine_id,customer_id,customer_session_id,business_date,status,started_at,started_by,start_key) values (?,? ,?,? ,?,'ACTIVE',?,?,?)";
                    insert(first,insert,m1,customer,session,actor,date,"concurrent-1");
                    var executor=Executors.newSingleThreadExecutor();var begun=new CountDownLatch(1);
                    try {
                        Future<String> result=executor.submit(()->{begun.countDown();try{insert(second,insert,m1,otherCustomer,other,actor,date,"concurrent-2");second.commit();return "unexpected";}catch(SQLException e){second.rollback();return e.getSQLState();}});
                        assertThat(begun.await(5,TimeUnit.SECONDS)).isTrue();
                        assertThatThrownBy(()->result.get(150,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                        first.commit();
                        assertThat(result.get(10,TimeUnit.SECONDS)).isEqualTo("23505");
                    }finally{executor.shutdownNow();}
                }
                try(var result=st.executeQuery("select status,exit_time from "+schema+".customer_sessions where id='"+session+"'")) {
                    result.next();assertThat(result.getString(1)).isEqualTo("OPEN");assertThat(result.getTimestamp(2)).isNull();
                }
                st.execute("update "+schema+".customer_sessions set exit_time=timestamp '2026-09-15 11:00' where id='"+other+"'");
                assertThat(repo.candidates("C2",date)).isEmpty();
            }finally{st.execute("drop schema "+schema+" cascade");}
        }
    }
    static String rewrite(String sql,String schema){return sql.replace("casino.",schema+".").replace("customer.",schema+".").replace("session.",schema+".").replace("core.",schema+".");}
    static void insert(Connection c,String sql,UUID machine,UUID customer,UUID session,UUID actor,LocalDate date,String key)throws SQLException {
        try(var p=c.prepareStatement(sql)){p.setObject(1,UUID.randomUUID());p.setObject(2,machine);p.setObject(3,customer);p.setObject(4,session);p.setObject(5,date);p.setObject(6,date.atTime(11,0));p.setObject(7,actor);p.setString(8,key);p.executeUpdate();}
    }
}
