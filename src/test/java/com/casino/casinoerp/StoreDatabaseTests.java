package com.casino.casinoerp;

import com.casino.casinoerp.dto.StoreDtos.*;
import com.casino.casinoerp.entity.User;
import com.casino.casinoerp.entity.BusinessDate;
import com.casino.casinoerp.repository.StoreRepository;
import com.casino.casinoerp.service.*;
import com.casino.casinoerp.security.Role;
import com.casino.casinoerp.exception.ResourceConflictException;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Explicit disposable cluster only. No fallback to application, dev, ST1 or ST1-B datasources. */
class StoreDatabaseTests {
    JdbcTemplate jdbc; StoreService service; StoreRepository repo;
    volatile Runnable afterRequestHeader;
    CurrentUserRoleService roles; AuthenticatedUserService users; AuditLogService audit; BusinessDateService dates;
    String schema; UUID actor=UUID.randomUUID(),staff=UUID.randomUUID(),department=UUID.randomUUID();
    @BeforeEach void setup() throws Exception {
        String url=System.getenv("SP1_TEST_JDBC_URL");
        if(url==null || !url.matches("jdbc:postgresql://(127\\.0\\.0\\.1|localhost):55436/casino_sp1_test"))throw new IllegalStateException("SP1_TEST_JDBC_URL must select the isolated port 55436 casino_sp1_test database.");
        var ds=new DriverManagerDataSource(url,System.getProperty("user.name"),"");jdbc=new JdbcTemplate(ds);
        schema="sp1_"+UUID.randomUUID().toString().replace("-","");jdbc.execute("create schema "+schema);
        jdbc.execute(sql("create table core.users(id uuid primary key,username text,status text)"));
        jdbc.execute(sql("create table casino.departments(id uuid primary key,name text,active boolean)"));
        jdbc.execute(sql("create table casino.staff_profiles(id uuid primary key,user_id uuid references core.users(id),department_id uuid references casino.departments(id),employee_code text,employment_status text,created_at timestamp)"));
        jdbc.execute(sql("create table casino.test_audit(id uuid primary key,action text,actor uuid,business_date date,details text)"));
        try(var in=getClass().getResourceAsStream("/db/migration/V36__add_store_purchase_foundation.sql")){jdbc.execute(sql(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)));}
        jdbc.update(sql("insert into core.users values(?, 'Operator','ACTIVE')"),actor);
        jdbc.update(sql("insert into casino.departments values(?,'Kitchen',true)"),department);
        jdbc.update(sql("insert into casino.staff_profiles values(?,?,?,'E1','ACTIVE',now())"),staff,actor,department);
        var named=new NamedParameterJdbcTemplate(ds) {
            @Override public List<Map<String,Object>> queryForList(String s,Map<String,?> a){return super.queryForList(sql(s),a);}
            @Override public int update(String s,Map<String,?> a){return super.update(sql(s),a);}
            @Override public <T> List<T> query(String s,Map<String,?> a,RowMapper<T> m){var result=super.query(sql(s),a,m);var hook=afterRequestHeader;if(hook!=null&&s.contains("where r.id=:id"))hook.run();return result;}
        };
        repo=new StoreRepository(named);roles=mock(CurrentUserRoleService.class);users=mock(AuthenticatedUserService.class);dates=mock(BusinessDateService.class);audit=mock(AuditLogService.class);
        when(roles.getCurrentRole()).thenReturn(Optional.of(Role.SUPER_ADMIN));var user=new User();user.setId(actor);when(users.getRequiredUser()).thenReturn(user);
        when(dates.getCurrentOpenBusinessDate()).thenReturn(Optional.empty());var time=new AtomicLong();when(dates.currentCasinoDateTime()).thenAnswer(i->LocalDateTime.of(2026,9,18,10,0).plusNanos(time.incrementAndGet()*1000));
        doAnswer(i->{jdbc.update(sql("insert into casino.test_audit values(?,?,?,?,?)"),UUID.randomUUID(),i.getArgument(1),i.getArgument(4),i.getArgument(0),i.getArgument(5));return null;}).when(audit).logForBusinessDate(any(),anyString(),eq("STORE"),any(),any(),anyString());
        var proxy=new ProxyFactory(new StoreService(repo,roles,users,dates,audit));proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new JdbcTransactionManager(ds),new AnnotationTransactionAttributeSource()));service=(StoreService)proxy.getProxy();
    }
    String sql(String s){return s.replace("casino.",schema+".").replace("core.",schema+".");}
    @AfterEach void cleanup(){if(jdbc!=null&&schema!=null)jdbc.execute("drop schema "+schema+" cascade");}
    Quantity qty(int n){return new Quantity(n,UUID.randomUUID().toString(),null);}
    UUID item(){return service.item(null,new ItemWrite("I-"+UUID.randomUUID(),"Item","Goods",Unit.PCS,true,null)).id();}
    UUID request(UUID item,int n){return service.createRequest(new RequestCreate(staff,null,"private remark",List.of(new RequestLine(item,n)),UUID.randomUUID().toString())).id();}
    Line line(UUID request){return service.detail(request).lines().getFirst();}
    int balance(UUID item){return jdbc.queryForObject(sql("select quantity_balance from casino.store_items where id=?"),Integer.class,item);}
    Transition transition(long v){return new Transition(v,"private cancellation reason","private supplier");}
    long count(String table){return jdbc.queryForObject("select count(*) from "+schema+"."+table,Long.class);}
    @Test void completeQuantityWorkflowAndIsolation() {
        UUID item=item();service.opening(item,qty(6));UUID request=request(item,10),line=line(request).id();
        service.issue(line,qty(6));assertThat(service.detail(request).request().status()).isEqualTo("PARTIALLY_FULFILLED");assertThat(balance(item)).isZero();assertThat(line(request).outstandingQuantity()).isEqualTo(4);
        UUID procurement=service.procure(line,qty(4)).id();service.transition(procurement,transition(0),true);var receipt=qty(4);var result=service.receive(procurement,receipt);
        assertThat(balance(item)).isEqualTo(4);assertThat(service.detail(request).request().status()).isEqualTo("PARTIALLY_FULFILLED");assertThat(service.receive(procurement,receipt)).isEqualTo(result);assertThat(balance(item)).isEqualTo(4);
        service.issue(line,qty(4));assertThat(balance(item)).isZero();assertThat(service.detail(request).request().status()).isEqualTo("FULFILLED");
        var ledger=service.movements("","",item,0,50).items().reversed();assertThat(ledger).extracting(Movement::movementType).containsExactly("OPENING","ISSUE","RECEIPT","ISSUE");assertThat(ledger).extracting(Movement::balanceAfter).containsExactly(6,0,4,0);assertThat(ledger).allMatch(m->m.businessDate()==null);assertThat(ledger.get(2).departmentName()).isEqualTo("Kitchen");assertThat(ledger.get(2).requestReference()).isEqualTo(service.detail(request).request().reference());
        assertThat(jdbc.queryForList(sql("select details from casino.test_audit"),String.class)).allMatch(s->!s.contains("private"));
        assertThat(jdbc.queryForList(sql("select actor from casino.test_audit"),UUID.class)).containsOnly(actor);
        // No financial repositories/tables are installed: this complete service workflow cannot depend on them.
        verify(dates,never()).getCurrentBusinessDate();verify(dates,never()).validateNewOperationalMutationAllowed();verify(dates,never()).validateSettlementMutationAllowed();
    }
    @Test void conflictingIssuesSerializeWithoutLostBalanceOrProgress()throws Exception {
        UUID item=item();service.opening(item,qty(10));UUID a=request(item,8),b=request(item,8);
        assertThat(race(()->service.issue(line(a).id(),qty(8)),()->service.issue(line(b).id(),qty(8)))).containsExactlyInAnyOrder(true,false);
        assertThat(balance(item)).isEqualTo(2);assertThat(count("store_movements")).isEqualTo(2);assertThat(line(a).issuedQuantity()+line(b).issuedQuantity()).isEqualTo(8);
    }
    @Test void issueCancellationRaceMaintainsQuantities()throws Exception {
        UUID item=item();service.opening(item,qty(10));UUID r=request(item,10),line=line(r).id();
        var result=race(()->service.issue(line,qty(8)),()->service.cancel(r,transition(0)));
        assertThat(result).contains(true);Line l=line(r);assertThat(l.issuedQuantity()+l.cancelledQuantity()).isLessThanOrEqualTo(10);assertThat(balance(item)).isEqualTo(10-l.issuedQuantity());
    }
    @Test void concurrentReceiptsAndSameKeyReplay()throws Exception {
        UUID item=item(),r=request(item,10),p=service.procure(line(r).id(),qty(10)).id();service.transition(p,transition(0),true);
        var q=qty(8);assertThat(race(()->service.receive(p,q),()->service.receive(p,q))).containsOnly(true);assertThat(balance(item)).isEqualTo(8);
        assertThatThrownBy(()->service.receive(p,qty(3))).isInstanceOf(ResourceConflictException.class);
        assertThat(count("store_movements")).isEqualTo(1);
    }
    @Test void detailHeaderAndLinesShareSnapshotDuringConcurrentIssue() throws Exception {
        UUID item=item();service.opening(item,qty(2));UUID r=request(item,2),lineId=line(r).id();
        var headerRead=new CountDownLatch(1);var continueRead=new CountDownLatch(1);
        afterRequestHeader=()->{headerRead.countDown();try{if(!continueRead.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Read timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}};
        try(var executor=Executors.newSingleThreadExecutor()) {
            var reading=executor.submit(()->service.detail(r));
            try{assertThat(headerRead.await(5,TimeUnit.SECONDS)).isTrue();service.issue(lineId,qty(2));}finally{continueRead.countDown();}
            var snapshot=reading.get(5,TimeUnit.SECONDS);assertThat(snapshot.request().status()).isEqualTo("PENDING");assertThat(snapshot.lines().getFirst().issuedQuantity()).isZero();
        } finally{afterRequestHeader=null;continueRead.countDown();}
        assertThat(service.detail(r).request().status()).isEqualTo("FULFILLED");assertThat(line(r).issuedQuantity()).isEqualTo(2);
    }
    @Test void incompatibleReceiptKeysCannotOverReceive() throws Exception {
        UUID item=item(),r=request(item,10),p=service.procure(line(r).id(),qty(10)).id();service.transition(p,transition(0),true);
        assertThat(race(()->service.receive(p,qty(8)),()->service.receive(p,qty(8)))).containsExactlyInAnyOrder(true,false);
        assertThat(balance(item)).isEqualTo(8);assertThat(count("store_movements")).isEqualTo(1);
        var row=service.procurements("","",0,50).items().getFirst();assertThat(row.receivedQuantity()).isEqualTo(8);assertThat(row.outstandingQuantity()).isEqualTo(2);
        assertThat(line(r).issuedQuantity()).isZero();
    }
    @Test void replaySurvivesInactiveItemAndCompletedLifecycleButRejectsActorOrPayload() {
        UUID item=item();var opening=qty(5);var original=service.opening(item,opening);
        var row=service.items("",null,0,50).items().getFirst();service.item(item,new ItemWrite(row.code(),row.name(),row.category(),Unit.PCS,false,row.version()));
        assertThat(service.opening(item,opening)).isEqualTo(original);
        assertThatThrownBy(()->service.opening(item,new Quantity(6,opening.idempotencyKey(),null))).isInstanceOf(ResourceConflictException.class);
        var other=new User();other.setId(UUID.randomUUID());when(users.getRequiredUser()).thenReturn(other);
        assertThatThrownBy(()->service.opening(item,opening)).isInstanceOf(ResourceConflictException.class);assertThat(balance(item)).isEqualTo(5);
    }
    @Test void createReplayNormalizationAndGlobalKeyConflicts() {
        UUID item=item();String key="create-key";var r=new RequestCreate(staff,null," note ",List.of(new RequestLine(item,10)),key);
        var result=service.createRequest(r);assertThat(service.createRequest(new RequestCreate(staff,null,"note",r.lines(),key))).isEqualTo(result);assertThat(count("store_requests")).isEqualTo(1);
        assertThatThrownBy(()->service.opening(item,new Quantity(1,key,null))).isInstanceOf(ResourceConflictException.class);
        var q=qty(4);UUID p=service.procure(line(result.id()).id(),q).id();service.transition(p,transition(0),true);assertThat(service.procure(line(result.id()).id(),q).id()).isEqualTo(p);
    }
    @Test void activeProcurementBlocksCancellationAndPartialCancellationPreservesReceipts() {
        UUID item=item(),r=request(item,10),p=service.procure(line(r).id(),qty(10)).id();
        assertThatThrownBy(()->service.cancel(r,transition(0))).isInstanceOf(ResourceConflictException.class);
        service.transition(p,transition(0),true);service.receive(p,qty(4));service.transition(p,transition(2),false);service.cancel(r,transition(0));
        assertThat(balance(item)).isEqualTo(4);assertThat(line(r).cancelledQuantity()).isEqualTo(10);assertThat(service.detail(r).request().status()).isEqualTo("CANCELLED");assertThatThrownBy(()->service.receive(p,qty(1))).isInstanceOf(ResourceConflictException.class);
    }
    @Test void receiptNotLimitedByLaterRequestFulfilment() {
        UUID item=item(),r=request(item,4),p=service.procure(line(r).id(),qty(4)).id();service.transition(p,transition(0),true);
        service.opening(item,qty(4));service.issue(line(r).id(),qty(4));service.receive(p,qty(4));assertThat(balance(item)).isEqualTo(4);assertThat(service.detail(r).request().status()).isEqualTo("FULFILLED");
    }
    @Test void auditFailureRollsBackBalanceMovementAndProgress() {
        UUID item=item();service.opening(item,qty(10));UUID r=request(item,10);long before=count("store_movements");
        doThrow(new IllegalStateException("audit failure")).when(audit).logForBusinessDate(any(),eq("STORE_STOCK_ISSUED"),anyString(),any(),any(),anyString());
        assertThatThrownBy(()->service.issue(line(r).id(),qty(6))).isInstanceOf(IllegalStateException.class);
        assertThat(balance(item)).isEqualTo(10);assertThat(count("store_movements")).isEqualTo(before);assertThat(line(r).issuedQuantity()).isZero();assertThat(service.detail(r).request().version()).isZero();
    }
    @Test void openingAdjustmentVersionAndHistoryRules() {
        UUID item=item();UUID r=request(item,1);var i=service.items("",null,0,50).items().getFirst();
        assertThatThrownBy(()->service.item(item,new ItemWrite("NEW","Item","Goods",Unit.PCS,true,i.version()))).isInstanceOf(ResourceConflictException.class);
        assertThatThrownBy(()->service.item(item,new ItemWrite(i.code(),"Item","Goods",Unit.BOX,true,i.version()))).isInstanceOf(ResourceConflictException.class);
        var q=qty(2);service.opening(item,q);assertThat(service.opening(item,q)).isNotNull();assertThatThrownBy(()->service.opening(item,qty(1))).isInstanceOf(ResourceConflictException.class);
        var a=new Adjustment(MovementType.ADJUSTMENT_OUT,2,"count correction","adjust-key");var result=service.adjust(item,a);assertThat(service.adjust(item,a)).isEqualTo(result);assertThat(balance(item)).isZero();
        assertThatThrownBy(()->service.adjust(item,new Adjustment(MovementType.ADJUSTMENT_OUT,1,"x","other"))).isInstanceOf(ResourceConflictException.class);
        assertThatThrownBy(()->service.adjust(item,new Adjustment(MovementType.ADJUSTMENT_IN,1," ","other"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.item(item,new ItemWrite(i.code(),"Item","Goods",Unit.PCS,true,i.version()))).isInstanceOf(ResourceConflictException.class);
    }
    @Test void dateIsPersistedOpenSnapshotOnlyAndStoreDoesNotUseSystemLock() {
        var lock=new SystemLockService(roles,mock(RolePermissionService.class),audit,Clock.fixed(Instant.parse("2026-09-18T03:00:00Z"),ZoneId.of("Asia/Kathmandu")));
        assertThat(lock.isSystemLocked()).isTrue();
        var date=new BusinessDate();date.setBusinessDate(LocalDate.of(2026,9,17));when(dates.getCurrentOpenBusinessDate()).thenReturn(Optional.of(date));
        UUID item=item();service.opening(item,qty(1));assertThat(service.movements("","",item,0,50).items().getFirst().businessDate()).isEqualTo(date.getBusinessDate());
        assertThat(Arrays.stream(StoreService.class.getDeclaredFields()).anyMatch(f->f.getType()==SystemLockService.class)).isFalse();
    }
    @Test void readsBoundedAndInactiveHistoryVisible() {
        UUID item=item();request(item,1);for(int n=0;n<4;n++)item();
        var page=service.items("",null,0,2);assertThat(page.hasNext()).isTrue();assertThat(page.items()).hasSize(2);assertThat(service.items("",null,0,2)).isEqualTo(page);assertThat(service.items("",null,2,2).hasNext()).isFalse();
        assertThat(service.items("' OR 1=1 --",null,0,50).items()).isEmpty();
        for(int[] args:List.of(new int[]{-1,50},new int[]{0,0},new int[]{0,101},new int[]{Integer.MAX_VALUE,100}))assertThatThrownBy(()->service.items("",null,args[0],args[1])).isInstanceOf(IllegalArgumentException.class);
        jdbc.update(sql("update core.users set status='INACTIVE' where id=?"),actor);assertThat(service.requests("","",0,50).items()).hasSize(1);assertThat(service.staff("",0,50).items()).isEmpty();
    }
    @Test void databaseConstraintsAndImmutableLedger() {
        UUID item=item();service.opening(item,qty(3));UUID r=request(item,4),line=line(r).id();
        for(String statement:List.of("update casino.store_items set quantity_balance=-1", "update casino.store_items set code=' lowercase '","update casino.store_request_lines set issued_quantity=5","update casino.store_request_lines set requested_quantity=0", "update casino.store_request_lines set requested_quantity=requested_quantity+1","delete from casino.store_items","delete from casino.store_movements","update casino.store_movements set quantity=1","delete from core.users","update casino.store_requests set status='APPROVED'"))
            assertThatThrownBy(()->jdbc.execute(sql(statement))).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update(sql("insert into casino.store_request_lines values(?,?,?,?,?,?)"),UUID.randomUUID(),r,item,1,0,0)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update(sql("insert into casino.store_request_lines values(?,?,?,?,?,?)"),UUID.randomUUID(),UUID.randomUUID(),item,1,0,0)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(balance(item)).isEqualTo(3);
    }
    @Test void serviceAuthorizationEveryRole() {
        UUID item=item();for(Role role:Role.values()) {
            when(roles.getCurrentRole()).thenReturn(Optional.of(role));
            if(role==Role.SUPER_ADMIN||role==Role.DIRECTOR)assertThat(service.items("",null,0,50).items()).hasSize(1);
            else assertThatThrownBy(()->service.items("",null,0,50)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            if(role!=Role.SUPER_ADMIN)assertThatThrownBy(()->service.opening(item,qty(1))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        }
    }
    @Test void insertConstraintsCoverUniqueKeysReferencesAndQuantityBounds() {
        UUID item=item();service.opening(item,qty(2));UUID r=request(item,5),p=service.procure(line(r).id(),qty(5)).id();
        var itemCopy=new LinkedHashMap<>(jdbc.queryForMap(sql("select * from casino.store_items where id='"+item+"'")));itemCopy.put("id",UUID.randomUUID());
        assertThatThrownBy(()->insertCopy("store_items",itemCopy)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        itemCopy.put("code"," lowercase ");assertThatThrownBy(()->insertCopy("store_items",itemCopy)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        var movement=jdbc.queryForMap(sql("select * from casino.store_movements limit 1"));
        // Each candidate has a fresh PK, so the intended constraint rather than PK uniqueness is exercised.
        for(var patch:List.of(Map.<String,Object>of("reference","OTHER","idempotency_key","OTHER","quantity",0),
            Map.<String,Object>of("reference","OTHER","idempotency_key","OTHER","movement_type","ISSUE"),
            Map.<String,Object>of("reference","OTHER","idempotency_key","OTHER","movement_type","ADJUSTMENT_IN","reason",""),
            Map.<String,Object>of("reference","OTHER","idempotency_key","OTHER","balance_after",-1),
            Map.<String,Object>of("reference","OTHER","idempotency_key","OTHER"), // second opening
            Map.<String,Object>of("movement_type","ADJUSTMENT_IN","reason","x","idempotency_key","OTHER"), // duplicate reference
            Map.<String,Object>of("movement_type","ADJUSTMENT_IN","reason","x","reference","OTHER"))) { // duplicate retry
            var copy=new LinkedHashMap<>(movement);copy.put("id",UUID.randomUUID());copy.putAll(patch);
            assertThatThrownBy(()->insertCopy("store_movements",copy)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        }
        for(String statement:List.of("update casino.store_procurements set received_quantity=quantity+1", "update casino.store_procurements set quantity=0", "update casino.store_procurements set status='RECEIVED'", "update casino.store_procurements set status='UNKNOWN'", "delete from casino.staff_profiles", "delete from casino.departments"))
            assertThatThrownBy(()->jdbc.execute(sql(statement))).isInstanceOf(org.springframework.dao.DataAccessException.class);
        var request=jdbc.queryForMap(sql("select * from casino.store_requests limit 1"));
        for(String field:List.of("reference","creation_retry_key")){
            var copy=new LinkedHashMap<>(request);copy.put("id",UUID.randomUUID());copy.put(field,"different");
            assertThatThrownBy(()->insertCopy("store_requests",copy)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        }
        UUID other=item();var invalid=new LinkedHashMap<>(movement);invalid.put("id",UUID.randomUUID());invalid.put("reference","DIFFERENT");invalid.put("idempotency_key","DIFFERENT");invalid.put("movement_type","RECEIPT");invalid.put("item_id",other);invalid.put("procurement_id",p);
        assertThatThrownBy(()->insertCopy("store_movements",invalid)).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    void insertCopy(String table,Map<String,Object> values) {
        values=new LinkedHashMap<>(values);values.remove("ledger_order");
        jdbc.update("insert into "+schema+"."+table+"("+String.join(",",values.keySet())+") values("+String.join(",",Collections.nCopies(values.size(),"?"))+")",values.values().toArray());
    }
    @Test void receiptAuditFailureRollsBackProcurementProgressAndLedger() {
        UUID item=item(),r=request(item,5),p=service.procure(line(r).id(),qty(5)).id();service.transition(p,transition(0),true);
        doThrow(new IllegalStateException("audit failure")).when(audit).logForBusinessDate(any(),eq("STORE_GOODS_RECEIVED"),anyString(),any(),any(),anyString());
        assertThatThrownBy(()->service.receive(p,qty(3))).isInstanceOf(IllegalStateException.class);
        assertThat(balance(item)).isZero();assertThat(count("store_movements")).isZero();assertThat(service.procurements("","",0,50).items().getFirst().receivedQuantity()).isZero();
    }
    @Test void normalizedCodesAndPartialRequestCancellation() {
        var a=service.item(null,new ItemWrite(" abc ","Name","Category",Unit.BOX,true,null));
        assertThat(a.reference()).isEqualTo("ABC");assertThatThrownBy(()->service.item(null,new ItemWrite("ABC","Other","Category",Unit.BOX,true,null))).isInstanceOf(ResourceConflictException.class);
        service.opening(a.id(),qty(2));UUID r=request(a.id(),5);service.issue(line(r).id(),qty(2));service.cancel(r,transition(1));
        assertThat(line(r).issuedQuantity()).isEqualTo(2);assertThat(line(r).cancelledQuantity()).isEqualTo(3);assertThat(balance(a.id())).isZero();
    }
    @Test void cannotInactivateWhileDemandOrActiveProcurementRemains() {
        UUID item=item(),r=request(item,2);
        assertThatThrownBy(()->setActive(item,false)).isInstanceOf(ResourceConflictException.class);
        UUID p=service.procure(line(r).id(),qty(2)).id();service.transition(p,transition(0),true);
        service.opening(item,qty(2));service.issue(line(r).id(),qty(2));
        // Fulfilled request does not remove the obligation to receive an already ordered procurement.
        assertThatThrownBy(()->setActive(item,false)).isInstanceOf(ResourceConflictException.class);
        service.receive(p,qty(2));setActive(item,false);
        assertThat(service.items("",false,0,50).items()).hasSize(1);
        assertThat(service.movements("","",item,0,50).items()).hasSize(3);
    }
    void setActive(UUID id,boolean active) {
        var row=service.items("",null,0,50).items().stream().filter(i->i.id().equals(id)).findFirst().orElseThrow();
        service.item(id,new ItemWrite(row.code(),row.name(),row.category(),Unit.valueOf(row.unit()),active,row.version()));
    }
    @Test void ledgerOrderPreservesBalanceSequenceAcrossClockCorrections() {
        UUID item=item();when(dates.currentCasinoDateTime()).thenReturn(LocalDateTime.of(2026,9,18,10,0));
        service.opening(item,new Quantity(6,"tie-opening",null));
        // A wall-clock correction must not reverse the committed stock sequence.
        when(dates.currentCasinoDateTime()).thenReturn(LocalDateTime.of(2026,9,18,9,59));
        service.adjust(item,new Adjustment(MovementType.ADJUSTMENT_OUT,2,"count","tie-adjustment"));
        var ledger=service.movements("","",item,0,50).items().reversed();
        assertThat(ledger).extracting(Movement::movementType).containsExactly("OPENING","ADJUSTMENT_OUT");
        assertThat(ledger).extracting(Movement::balanceAfter).containsExactly(6,4);
    }
    @Test void integerMaximumBalancesAndMultilineTotalsRemainExact() {
        UUID item=item(),second=item();service.opening(item,qty(Integer.MAX_VALUE));
        long movements=count("store_movements"),events=count("test_audit");
        assertThatThrownBy(()->service.adjust(item,new Adjustment(MovementType.ADJUSTMENT_IN,1,"capacity","overflow"))).isInstanceOf(ResourceConflictException.class);
        assertThat(balance(item)).isEqualTo(Integer.MAX_VALUE);assertThat(count("store_movements")).isEqualTo(movements);assertThat(count("test_audit")).isEqualTo(events);
        var request=new RequestCreate(staff,null,null,List.of(new RequestLine(item,Integer.MAX_VALUE),new RequestLine(second,Integer.MAX_VALUE)),"maximum-request");
        UUID r=service.createRequest(request).id();var first=service.detail(r).lines().stream().filter(l->l.itemId().equals(item)).findFirst().orElseThrow();
        service.issue(first.id(),qty(Integer.MAX_VALUE));assertThat(balance(item)).isZero();assertThat(service.detail(r).request().status()).isEqualTo("PARTIALLY_FULFILLED");
        service.cancel(r,transition(1));assertThat(service.detail(r).request().status()).isEqualTo("CANCELLED");
        assertThat(service.detail(r).lines()).allMatch(l->l.outstandingQuantity()==0);
    }
    @Test void equivalentCodesConcurrentOpeningAndActiveProcurementSerialize()throws Exception {
        var codes=race(()->service.item(null,new ItemWrite(" review-code ","Name","Goods",Unit.PCS,true,null)),()->service.item(null,new ItemWrite("REVIEW-CODE","Name","Goods",Unit.PCS,true,null)));
        assertThat(codes).containsExactlyInAnyOrder(true,false);
        UUID item=item();assertThat(race(()->service.opening(item,qty(10)),()->service.opening(item,qty(10)))).containsExactlyInAnyOrder(true,false);
        assertThat(balance(item)).isEqualTo(10);UUID r=request(item,10),l=line(r).id();
        assertThat(race(()->service.procure(l,qty(10)),()->service.procure(l,qty(10)))).containsExactlyInAnyOrder(true,false);
        assertThat(count("store_procurements")).isEqualTo(1);
    }
    @Test void receiptAndCancellationSerializeWithoutLosingCommittedStock()throws Exception {
        UUID item=item(),r=request(item,10),p=service.procure(line(r).id(),qty(10)).id();service.transition(p,transition(0),true);
        assertThat(race(()->service.receive(p,qty(4)),()->service.transition(p,transition(1),false))).containsExactlyInAnyOrder(true,false);
        var result=service.procurements("","",0,50).items().getFirst();
        assertThat(balance(item)).isEqualTo(result.receivedQuantity());
        assertThat(count("store_movements")).isEqualTo(result.receivedQuantity()==0?0:1);
        if(result.status().equals("ORDERED"))service.transition(p,transition(result.version()),false);
        assertThatThrownBy(()->service.receive(p,qty(1))).isInstanceOf(ResourceConflictException.class);
    }
    @Test void reorderedRequestReplayAndCompletedIssueReplayAreStable() {
        UUID a=item(),b=item();var lines=List.of(new RequestLine(a,2),new RequestLine(b,3));
        var original=service.createRequest(new RequestCreate(staff,null," note ",lines,"reordered"));
        assertThat(service.createRequest(new RequestCreate(staff,null,"note",lines.reversed(),"reordered"))).isEqualTo(original);
        service.opening(a,qty(2));service.opening(b,qty(3));
        for(var line:service.detail(original.id()).lines()) {var q=qty(line.requestedQuantity());var result=service.issue(line.id(),q);assertThat(service.issue(line.id(),q)).isEqualTo(result);}
        assertThat(service.detail(original.id()).request().status()).isEqualTo("FULFILLED");assertThat(count("store_movements")).isEqualTo(4);
    }
    List<Boolean> race(Runnable a,Runnable b)throws Exception {
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {var tasks=new ArrayList<Future<Boolean>>();for(var run:List.of(a,b))tasks.add(pool.submit(()->{start.await();try{run.run();return true;}catch(ResourceConflictException e){return false;}}));start.countDown();return List.of(tasks.get(0).get(15,TimeUnit.SECONDS),tasks.get(1).get(15,TimeUnit.SECONDS));}
        finally{pool.shutdownNow();}
    }
}
