package com.casino.casinoerp;

import com.casino.casinoerp.dto.FnbDtos.*;
import com.casino.casinoerp.repository.FnbRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class FnbDatabaseTests {
 private String rewrite(String sql,String schema){return sql.replace("customer.",schema+".").replace("session.",schema+".").replace("core.",schema+".");}
 @Test void disposableMigrationQuantitiesHistoryAndRaces()throws Exception {
  Properties config=new Properties();try(var in=getClass().getResourceAsStream("/application.properties")){config.load(in);}
  var ds=new DriverManagerDataSource(config.getProperty("spring.datasource.url"),config.getProperty("spring.datasource.username"),config.getProperty("spring.datasource.password"));
  String schema="fb1_test_"+UUID.randomUUID().toString().replace("-","");UUID customer=UUID.randomUUID(),actor=UUID.randomUUID(),session=UUID.randomUUID();
  try(var c=ds.getConnection();var st=c.createStatement()) {
   st.execute("create schema "+schema);
   try {
    st.execute("create table "+schema+".customers(id uuid primary key,customer_code text,full_name text)");
    st.execute("create table "+schema+".users(id uuid primary key,username text)");
    st.execute("create table "+schema+".customer_sessions(id uuid primary key)");
    st.execute("insert into "+schema+".customers values('"+customer+"','C1','Guest')");
    st.execute("insert into "+schema+".users values('"+actor+"','Handler')");
    st.execute("insert into "+schema+".customer_sessions values('"+session+"')");
    try(var in=getClass().getResourceAsStream("/db/migration/V35__add_complimentary_fnb_requests.sql")){st.execute(rewrite(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8),schema));}
    var jdbc=new NamedParameterJdbcTemplate(ds){
     @Override public Map<String,Object> queryForMap(String sql,Map<String,?> args){return super.queryForMap(rewrite(sql,schema),args);}
     @Override public List<Map<String,Object>> queryForList(String sql,Map<String,?> args){return super.queryForList(rewrite(sql,schema),args);}
     @Override public int update(String sql,Map<String,?> args){return super.update(rewrite(sql,schema),args);}

     @Override public Map<String,Object> queryForMap(String sql,SqlParameterSource args){return super.queryForMap(rewrite(sql,schema),args); }
     @Override public <T> List<T> query(String sql,SqlParameterSource args,org.springframework.jdbc.core.RowMapper<T> mapper){return super.query(rewrite(sql,schema),args,mapper);}
     @Override public List<Map<String,Object>> queryForList(String sql,SqlParameterSource args){return super.queryForList(rewrite(sql,schema),args);}
     @Override public int update(String sql,SqlParameterSource args){return super.update(rewrite(sql,schema),args);}
    };
    var repo=new FnbRepository(jdbc);var tx=new TransactionTemplate(new JdbcTransactionManager(ds));LocalDate date=LocalDate.of(2026,9,15);LocalDateTime now=date.atTime(10,0);
    Create food=new Create(customer,session,date,Type.FOOD,"Fried Rice",2,"Floor",null,"food");
    assertThat(repo.history(null,null,null,"","","",false)).isEmpty();assertThat(repo.overview(date).values()).allMatch(v->v==0);
    UUID id=tx.execute(s->repo.create(food,actor,date,now,"signature"));
    UUID beverage=tx.execute(s->repo.create(new Create(customer,null,date,Type.BEVERAGE,"Coke",3,"Bar",null,"drink"),actor,date,now,"drink"));
    assertThat(repo.history(date,null,null,"FOOD","","C1",false)).singleElement().satisfies(r->{assertThat(r.department()).isEqualTo("KITCHEN");assertThat(r.quantity()).isEqualTo(2);assertThat(r.requester()).isEqualTo("Handler");});
    assertThat(repo.history(date,null,null,"BEVERAGE","","",false)).singleElement().satisfies(r->assertThat(r.department()).isEqualTo("BAR"));
    assertThatThrownBy(()->tx.execute(s->repo.create(food,actor,date,now,"duplicate"))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(()->tx.execute(s->repo.create(new Create(UUID.randomUUID(),null,date,Type.FOOD,"x",1,"x",null,"orphan"),actor,date,now,"x"))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(()->tx.execute(s->repo.create(new Create(customer,null,date,Type.FOOD,"x",0,"x",null,"zero"),actor,date,now,"x"))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    // Same-key concurrent creates serialize and yield one receipt.
    var pool=Executors.newFixedThreadPool(2);
    try {
     var start=new CountDownLatch(1);Callable<UUID> create=()->{start.await();return tx.execute(s->{repo.retryLock("race");var replay=repo.replay("race");return replay==null?repo.create(new Create(customer,null,date,Type.FOOD,"Race",1,"Floor",null,"race"),actor,date,now,"race"):(UUID)replay.get("id");});};
     var a=pool.submit(create);var b=pool.submit(create);start.countDown();assertThat(a.get()).isEqualTo(b.get());
     var statusStart=new CountDownLatch(1);Callable<Boolean> change=()->{statusStart.await();return tx.execute(s->{var row=repo.lock(id);if(((Number)row.get("version")).intValue()!=0)return false;repo.change(id,new Change(Status.PREPARING,0),actor,now,date);return true;});};
     var x=pool.submit(change);var y=pool.submit(change);statusStart.countDown();assertThat(List.of(x.get(),y.get())).containsExactlyInAnyOrder(true,false);
    } finally {pool.shutdownNow();}
    tx.execute(s->{repo.lock(id);repo.change(id,new Change(Status.READY,1),actor,now,date);repo.change(id,new Change(Status.DELIVERED,2),actor,now.plusDays(1),date.plusDays(1));return null;});
    tx.execute(s->{repo.lock(beverage);repo.change(beverage,new Change(Status.PREPARING,0),actor,now,date);repo.change(beverage,new Change(Status.READY,1),actor,now,date);repo.change(beverage,new Change(Status.DELIVERED,2),actor,now,date);return null;});
    assertThat(repo.overview(date)).containsEntry("food",0L).containsEntry("beverage",3L).containsEntry("delivered",1L);
    assertThat(repo.overview(date.plusDays(1))).containsEntry("food",2L).containsEntry("beverage",0L);
    assertThat(repo.replayChange(id,new Change(Status.PREPARING,0),actor)).isTrue();assertThat(repo.replayChange(id,new Change(Status.PREPARING,0),UUID.randomUUID())).isFalse();
    assertThat(repo.history(null,null,null,"","","",true)).hasSize(1);
    for(int i=0;i<105;i++){int n=i;tx.execute(s->repo.create(new Create(customer,null,date,Type.FOOD,"History",1,"Floor",null,"many-"+n),actor,date.minusDays(1),now,"many"));}
    var history=repo.history(null,null,null,"","","",false);assertThat(history).hasSize(100);assertThat(repo.history(null,null,null,"","","",false)).isEqualTo(history);
    assertThat(repo.history(date,date,date,"","DELIVERED","",false)).hasSize(2);
    assertThat(repo.history(null,null,null,"","","' OR 1=1 --",false)).isEmpty();
    assertThat(repo.history(date,null,null,"FOOD","DELIVERED","",false)).singleElement().satisfies(r->{assertThat(r.businessDate()).isEqualTo(date);assertThat(r.deliveredAt()).isEqualTo(now.plusDays(1));});
    try(var rs=st.executeQuery("select column_name from information_schema.columns where table_schema='"+schema+"' and table_name='fnb_requests'")){while(rs.next())assertThat(rs.getString(1)).doesNotContain("cost","price","payment","amount");}
   } finally {st.execute("drop schema "+schema+" cascade");}
  }
 }
}
