package com.casino.casinoerp;
import com.casino.casinoerp.repository.CrmRepository;import com.casino.casinoerp.dto.CrmDtos.*;
import org.junit.jupiter.api.Test;import org.springframework.jdbc.core.namedparam.*;import org.springframework.jdbc.datasource.DriverManagerDataSource;import org.springframework.jdbc.support.JdbcTransactionManager;import org.springframework.transaction.support.TransactionTemplate;
import java.sql.*;import java.util.*;import java.time.*;import java.math.*;import java.nio.charset.StandardCharsets;import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
class CrmDatabaseTests {
 private static String rewrite(String sql,String schema){return sql.replace("customer.",schema+".").replace("session.",schema+".").replace("core.",schema+".");}
 @Test void migrationIsolationHistoryConstraintsAndConcurrency()throws Exception{
 Properties config=new Properties();try(var in=getClass().getResourceAsStream("/application.properties")){config.load(in);}
 var ds=new DriverManagerDataSource(config.getProperty("spring.datasource.url"),config.getProperty("spring.datasource.username"),config.getProperty("spring.datasource.password"));
 String schema="crm1_test_"+UUID.randomUUID().toString().replace("-","");UUID customer=UUID.randomUUID(),actor=UUID.randomUUID(),session=UUID.randomUUID(),legacy=UUID.randomUUID();
 try(var c=ds.getConnection();var st=c.createStatement()) {st.execute("create schema "+schema);try{
 st.execute("create table "+schema+".customers(id uuid primary key,customer_code text,full_name text)");st.execute("create table "+schema+".users(id uuid primary key,username text)");st.execute("create table "+schema+".customer_sessions(id uuid primary key,customer_id uuid)");
 st.execute("create table "+schema+".customer_service_records(id uuid primary key,customer_id uuid,customer_session_id uuid,business_date date,service_type varchar,service_description text,approved_by uuid,provided_by uuid,created_at timestamp,remarks text,service_cost numeric)");
 st.execute("insert into "+schema+".customers values('"+customer+"','C1','Customer')");st.execute("insert into "+schema+".users values('"+actor+"','Recorder')");st.execute("insert into "+schema+".customer_sessions values('"+session+"','"+customer+"')");st.execute("insert into "+schema+".customer_service_records(id,customer_id,customer_session_id,business_date,service_type,service_cost) values('"+legacy+"','"+customer+"','"+session+"','2026-09-15','OTHER',42)");
 for(String file:List.of("V13__add_hotel_bookings.sql","V33__add_crm_records.sql")){try(var in=getClass().getResourceAsStream("/db/migration/"+file)){st.execute(rewrite(new String(in.readAllBytes(),StandardCharsets.UTF_8),schema));}}
 var jdbc=new NamedParameterJdbcTemplate(ds){
 @Override public List<Map<String,Object>> queryForList(String sql,SqlParameterSource args){return super.queryForList(rewrite(sql,schema),args);}
 @Override public int update(String sql,SqlParameterSource args){return super.update(rewrite(sql,schema),args);}
 };
 var repo=new CrmRepository(jdbc);var tx=new TransactionTemplate(new JdbcTransactionManager(ds));LocalDate date=LocalDate.of(2026,9,15);LocalDateTime at=date.minusYears(1).atTime(12,0);
 var service=new ServiceCreate(customer,session,ServiceType.GIFT,"Gift",at,null,Classification.COMPLIMENTARY,null,"gift");
 UUID gift=repo.create(service,actor,date,at,"signature");UUID transport=repo.create(new TransportCreate(customer,null,TransportType.AIRPORT_PICKUP,"Airport","Hotel",at.plusYears(2),null,null,BigDecimal.ZERO,null,"transport"),actor,date,at,"transport-signature");
 st.execute("insert into "+schema+".hotel_bookings(id,booking_code,customer_id,business_date,hotel_name,room_type,check_in_date,check_out_date,number_of_guests,estimated_cost,billing_type,status,created_at,updated_at,created_by,idempotency_key) values('"+UUID.randomUUID()+"','HB', '"+customer+"','2026-09-15','Hotel','Room','2026-09-15','2026-09-16',1,25,'CUSTOMER_DIRECT','COMPLETED',now(),now(),'"+actor+"','hotel')");
 // Apply V34 to a populated V33 schema, not just an empty table.
 String before;
 try(var snapshot=st.executeQuery("select jsonb_agg(to_jsonb(s) order by id)::text from "+schema+".customer_service_records s")){snapshot.next();before=snapshot.getString(1);}
 try(var in=getClass().getResourceAsStream("/db/migration/V34__add_customer_service_record_foreign_keys.sql")){st.execute(rewrite(new String(in.readAllBytes(),StandardCharsets.UTF_8),schema));}
 try(var snapshot=st.executeQuery("select jsonb_agg(to_jsonb(s) order by id)::text from "+schema+".customer_service_records s")){snapshot.next();assertThat(snapshot.getString(1)).isEqualTo(before);}
 try(var constraints=st.executeQuery("select conname,confdeltype,confupdtype,convalidated from pg_constraint where conrelid='"+schema+".customer_service_records'::regclass and conname in ('fk_customer_service_records_customer','fk_customer_service_records_session') order by conname")){
  int count=0;while(constraints.next()){count++;assertThat(constraints.getString(2)).isEqualTo("a");assertThat(constraints.getString(3)).isEqualTo("a");assertThat(constraints.getBoolean(4)).isTrue();}assertThat(count).isEqualTo(2);
 }
 var history=repo.history("","","",null,null);assertThat(history).hasSize(3);assertThat(history.getFirst().get("recordType")).isEqualTo("TRANSPORT");assertThat(history).allMatch(row->row.get("customerCode").equals("C1")&&row.get("staff").equals("Recorder"));assertThat(history.stream().filter(row->row.get("id").equals(gift)).findFirst().orElseThrow().get("cost")).isNull();
 assertThat(repo.history("","GIFT","",date.minusYears(1),date.minusYears(1))).hasSize(1);assertThat(repo.customers("C1")).hasSize(1);assertThat(repo.history("","SERVICE","",null,null)).hasSize(1);
 try(var rs=st.executeQuery("select sum(service_cost) from "+schema+".customer_service_records where not crm_record")){rs.next();assertThat(rs.getBigDecimal(1)).isEqualByComparingTo("42");}
 assertThatThrownBy(()->repo.create(service,actor,date,at,"signature")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
 assertThatThrownBy(()->repo.create(new ServiceCreate(customer,null,ServiceType.OTHER,"x",at,new BigDecimal("-1"),Classification.UNSPECIFIED,null,"negative"),actor,date,at,"negative")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
 var executor=Executors.newFixedThreadPool(2);try{var start=new CountDownLatch(1);var a=executor.submit(()->{start.await();return tx.execute(s->repo.change(true,transport,new Change("COMPLETED",0L),at));});var b=executor.submit(()->{start.await();return tx.execute(s->repo.change(true,transport,new Change("CANCELLED",0L),at));});start.countDown();assertThat(a.get()+b.get()).isEqualTo(1);}finally{executor.shutdownNow();}
 tx.execute(s->{repo.retryLock("gift");assertThat(repo.replay(false,"gift").get("id")).isEqualTo(gift);return null;});
 for(int i=0;i<105;i++)repo.create(new ServiceCreate(customer,null,ServiceType.OTHER,"History",at,null,Classification.UNSPECIFIED,null,"many-"+i),actor,date,at,"many");
 var first=repo.history("","","",null,null);assertThat(first).hasSize(100);assertThat(repo.history("","","",null,null)).isEqualTo(first);
 // Execute the exact native queries used by ALL existing legacy service/report consumers.
 var legacyArgs=new MapSqlParameterSource().addValue("customerId",customer).addValue("customerSessionId",session).addValue("businessDate",date);
 for(var method:com.casino.casinoerp.repository.CustomerServiceRecordRepository.class.getDeclaredMethods()){
  var query=method.getAnnotation(org.springframework.data.jpa.repository.Query.class);
  if(query!=null){var found=jdbc.queryForList(query.value(),legacyArgs);assertThat(found).hasSize(1);assertThat(found.getFirst().get("id")).isEqualTo(legacy);}
 }
 var noDate=repo.create(new ServiceCreate(customer,null,ServiceType.FOOD,"Off-site",at,null,Classification.UNSPECIFIED,null,"no-date"),actor,null,at,"no-date");
 assertThat(repo.history("%off-site%","FOOD","",at.toLocalDate(),at.toLocalDate())).singleElement().satisfies(row->{assertThat(row.get("id")).isEqualTo(noDate);assertThat(row.get("business_date")).isNull();});
 assertThat(repo.history("%' or 1=1 --%","","",null,null)).isEmpty();
 assertThat(repo.change(false,gift,new Change("CANCELLED",0L),at)).isEqualTo(1);
 assertThat(repo.change(false,gift,new Change("CANCELLED",0L),at)).isZero();
 assertThat(repo.changedAlready(false,gift,new Change("CANCELLED",0L))).isTrue();
 // V34 must reject both kinds of orphan independently.
 org.assertj.core.api.SoftAssertions.assertSoftly(checks->{
  checks.assertThatThrownBy(()->repo.create(new ServiceCreate(UUID.randomUUID(),null,ServiceType.OTHER,"Orphan customer",at,null,Classification.UNSPECIFIED,null,"orphan-customer"),actor,date,at,"orphan-customer"))
   .as("CRM service customer foreign key must reject a nonexistent customer")
   .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  checks.assertThatThrownBy(()->repo.create(new ServiceCreate(customer,UUID.randomUUID(),ServiceType.OTHER,"Orphan session",at,null,Classification.UNSPECIFIED,null,"orphan-session"),actor,date,at,"orphan-session"))
   .as("CRM service session foreign key must reject a nonexistent session")
   .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
 });

 // Isolate deletion checks from hotel/transport foreign keys: only service records reference these parents.
 UUID fkCustomer=UUID.randomUUID(),fkSession=UUID.randomUUID(),legacyOptional=UUID.randomUUID();
 st.execute("insert into "+schema+".customers values('"+fkCustomer+"','FK','FK Customer')");
 st.execute("insert into "+schema+".customer_sessions values('"+fkSession+"','"+fkCustomer+"')");
 UUID linked=repo.create(new ServiceCreate(fkCustomer,fkSession,ServiceType.TICKET,"Linked",at,BigDecimal.ZERO,Classification.UNSPECIFIED,null,"linked-fk"),actor,null,at,"linked-fk");
 st.execute("insert into "+schema+".customer_service_records(id,customer_id,customer_session_id) values('"+legacyOptional+"','"+fkCustomer+"',null)");
 assertThatThrownBy(()->st.execute("delete from "+schema+".customers where id='"+fkCustomer+"'"))
  .isInstanceOf(SQLException.class).satisfies(e->assertThat(((SQLException)e).getSQLState()).isEqualTo("23503"));
 assertThatThrownBy(()->st.execute("delete from "+schema+".customer_sessions where id='"+fkSession+"'"))
  .isInstanceOf(SQLException.class).satisfies(e->assertThat(((SQLException)e).getSQLState()).isEqualTo("23503"));
 assertThatThrownBy(()->st.execute("update "+schema+".customer_service_records set customer_id='"+UUID.randomUUID()+"' where id='"+linked+"'"))
  .isInstanceOf(SQLException.class).satisfies(e->assertThat(((SQLException)e).getSQLState()).isEqualTo("23503"));
 assertThatThrownBy(()->st.execute("update "+schema+".customer_service_records set customer_session_id='"+UUID.randomUUID()+"' where id='"+linked+"'"))
  .isInstanceOf(SQLException.class).satisfies(e->assertThat(((SQLException)e).getSQLState()).isEqualTo("23503"));
 try(var remaining=st.executeQuery("select count(*) from "+schema+".customer_service_records where id in ('"+linked+"','"+legacyOptional+"')")){remaining.next();assertThat(remaining.getInt(1)).isEqualTo(2);}

 }finally{st.execute("drop schema "+schema+" cascade");}}
 }
}
