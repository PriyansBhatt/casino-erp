package com.casino.casinoerp;

import com.casino.casinoerp.dto.AccountsDtos.*;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.exception.ResourceNotFoundException;

import com.casino.casinoerp.repository.*;
import com.casino.casinoerp.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.nio.file.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Never loads application datasource configuration. Explicit disposable PostgreSQL only. */
class AccountsDatabaseTests {
    JdbcTemplate jdbc;AccountsService service;AccountsRepository repository;AuthenticatedUserService auth;
    BusinessDateService dates;BusinessDateRepository dateRepository;AuditLogService audit;AccountsEvidenceStorage storage;
    String schema;UUID preparer=UUID.randomUUID(),other=UUID.randomUUID(),manager=UUID.randomUUID(),director=UUID.randomUUID(),party;
    LocalDate day=LocalDate.of(2026,9,19);ThreadLocal<User> current=new ThreadLocal<>();@TempDir Path files;
    @BeforeEach void setup()throws Exception {
        String url=System.getenv("AC1_TEST_JDBC_URL");
        if(!"jdbc:postgresql://127.0.0.1:55437/casino_ac1_test".equals(url))throw new IllegalStateException("AC1 requires disposable port 55437 casino_ac1_test.");
        var ds=new DriverManagerDataSource(url,System.getProperty("user.name"),"");jdbc=new JdbcTemplate(ds);schema="ac1_"+UUID.randomUUID().toString().replace("-","");jdbc.execute("create schema "+schema);
        jdbc.execute(sql("create table core.users(id uuid primary key,username text)"));
        for(UUID id:List.of(preparer,other,manager,director))jdbc.update(sql("insert into core.users values(?,?)"),id,id.toString());
        jdbc.execute(sql("create table casino.test_audit(action text,actor uuid,business_date date,details text)"));
        try(var in=getClass().getResourceAsStream("/db/migration/V37__add_accounts_bill_workflow.sql")){jdbc.execute(sql(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)));}
        var named=new NamedParameterJdbcTemplate(ds){
            @Override public List<Map<String,Object>> queryForList(String s,Map<String,?> a){return super.queryForList(sql(s),a);}
            @Override public int update(String s,Map<String,?> a){return super.update(sql(s),a);}
        };
        repository=new AccountsRepository(named);auth=mock(AuthenticatedUserService.class);dates=mock(BusinessDateService.class);dateRepository=mock(BusinessDateRepository.class);audit=mock(AuditLogService.class);
        when(auth.getRequiredUser()).thenAnswer(i->current.get());var bd=new BusinessDate();bd.setBusinessDate(day);bd.setStatus("OPEN");when(dates.getCurrentOpenBusinessDate()).thenReturn(Optional.of(bd));when(dates.currentCasinoDateTime()).thenAnswer(i->LocalDateTime.now());
        doAnswer(i->{jdbc.update(sql("insert into casino.test_audit values(?,?,?,?)"),i.getArgument(1),i.getArgument(4),i.getArgument(0),i.getArgument(5));return null;}).when(audit).logForBusinessDate(any(),anyString(),eq("ACCOUNTS"),any(),any(),anyString());
        storage=new AccountsEvidenceStorage(files.toRealPath().toString());
        var proxy=new ProxyFactory(new AccountsService(repository,auth,dates,dateRepository,audit,storage,new ObjectMapper().findAndRegisterModules()));proxy.setProxyTargetClass(true);proxy.addAdvice(new TransactionInterceptor(new JdbcTransactionManager(ds),new AnnotationTransactionAttributeSource()));service=(AccountsService)proxy.getProxy();
        as(preparer,"STORE_MANAGER");party=service.party(new PartyWrite(key(),"P1","Supplier One","SUPPLIER")).id();
    }
    @AfterEach void cleanup(){if(jdbc!=null&&schema!=null)jdbc.execute("drop schema "+schema+" cascade");}
    String sql(String s){return s.replace("casino.",schema+".").replace("core.",schema+".").replace("customer.",schema+".");}
    void as(UUID id,String role){var user=new User();user.setId(id);user.setUsername(id.toString());user.setRole(role);user.setStatus("ACTIVE");current.set(user);}
    String key(){return UUID.randomUUID().toString();}
    Invoice invoice(String ref){return new Invoice(party,ref,day,day.plusDays(14),"NPR",new BigDecimal("100.10"),new BigDecimal("0.10"),new BigDecimal("13"),new BigDecimal("113"),List.of(new Line("Goods",new BigDecimal("100.10"))),List.of());}
    BillWrite write(String ref){return new BillWrite(key(),null,day,invoice(ref));}
    UUID create(){as(preparer,"STORE_MANAGER");return service.write(null,write(key())).id();}
    long version(UUID id){return jdbc.queryForObject(sql("select version from casino.accounts_bills where id=?"),Long.class,id);}
    String state(UUID id){return jdbc.queryForObject(sql("select status from casino.accounts_bills where id=?"),String.class,id);}
    Action action(UUID id){return new Action(key(),version(id),"Reason for review");}
    byte[] png()throws Exception {var out=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",out);return out.toByteArray();}
    void upload(UUID id)throws Exception{service.upload(id,action(id),"invoice.png","image/png",true,null,png());}
    void submit(UUID id)throws Exception{as(preparer,"STORE_MANAGER");upload(id);service.action(id,"SUBMIT",action(id));}
    void verify(UUID id)throws Exception{submit(id);as(manager,"ACCOUNTS_MANAGER");service.action(id,"VERIFY",action(id));}
    @Test void visibilityOwnershipAndFinalStates()throws Exception {
        UUID id=create();upload(id);UUID e=(UUID)((List<Map<String,Object>>)service.detail(id).get("evidence")).getFirst().get("id");
        for(String role:List.of("STORE_MANAGER","ACCOUNTANT_HEAD","ACCOUNTS_MANAGER","DIRECTOR","SUPER_ADMIN","CASHIER")) {
            as(other,role);assertThatThrownBy(()->service.detail(id)).isInstanceOf(RuntimeException.class);assertThatThrownBy(()->service.document(id,e)).isInstanceOf(RuntimeException.class);
            if(Set.of("SUPER_ADMIN","CASHIER").contains(role))assertThatThrownBy(()->service.bills("","",0,10)).isInstanceOf(RuntimeException.class);
            else {assertThat(service.bills("","",0,10).items()).isEmpty();assertThat(service.bills("Supplier","DRAFT",0,10).items()).isEmpty();}
        }
        as(preparer,"ACCOUNTANT_HEAD");service.action(id,"SUBMIT",action(id));as(manager,"ACCOUNTS_MANAGER");assertThat(service.detail(id)).isNotNull();service.action(id,"VERIFY",action(id));
        as(director,"DIRECTOR");service.action(id,"APPROVE",action(id));assertThat(state(id)).isEqualTo("APPROVED_FOR_PAYMENT");
        for(var pair:List.of(Map.entry(preparer,"STORE_MANAGER"),Map.entry(manager,"ACCOUNTS_MANAGER"),Map.entry(director,"DIRECTOR"))) {as(pair.getKey(),pair.getValue());assertThat(service.detail(id)).isNotNull();assertThat(service.document(id,e).bytes()).isEqualTo(png());assertThatThrownBy(()->service.action(id,"RETURN",action(id))).isInstanceOf(RuntimeException.class);}
        as(preparer,"STORE_MANAGER");assertThatThrownBy(()->service.write(id,new BillWrite(key(),version(id),null,invoice("new")))).isInstanceOf(RuntimeException.class);
    }
    @Test void holdReturnFreshVerificationAndDistinctReviewers()throws Exception {
        UUID id=create();verify(id);as(director,"DIRECTOR");service.action(id,"HOLD",action(id));
        assertThatThrownBy(()->service.action(id,"APPROVE",action(id))).isInstanceOf(RuntimeException.class);
        as(preparer,"STORE_MANAGER");assertThatThrownBy(()->upload(id)).isInstanceOf(RuntimeException.class);
        as(other,"DIRECTOR");service.action(id,"RESUME",action(id));assertThat(state(id)).isEqualTo("AWAITING_DIRECTOR_APPROVAL");service.action(id,"RETURN",action(id));
        assertThat(service.detail(id)).isNotNull();assertThatThrownBy(()->service.action(id,"APPROVE",action(id))).isInstanceOf(RuntimeException.class);
        as(manager,"ACCOUNTS_MANAGER");assertThat(service.detail(id)).isNotNull();
        as(preparer,"ACCOUNTANT_HEAD");service.action(id,"SUBMIT",action(id));as(manager,"ACCOUNTS_MANAGER");service.action(id,"VERIFY",action(id));
        as(manager,"DIRECTOR");assertThatThrownBy(()->service.action(id,"APPROVE",action(id))).hasMessageContaining("verifier");
        as(director,"DIRECTOR");service.action(id,"APPROVE",action(id));
        assertThat(jdbc.queryForObject(sql("select count(*) from casino.accounts_decisions where bill_id=? and action='VERIFY'"),Integer.class,id)).isEqualTo(2);
    }
    @Test void verificationHoldResumeAndFinalRejection()throws Exception {
        UUID id=create();submit(id);as(manager,"ACCOUNTS_MANAGER");service.action(id,"HOLD",action(id));
        assertThatThrownBy(()->service.action(id,"VERIFY",action(id))).isInstanceOf(RuntimeException.class);
        as(other,"ACCOUNTS_MANAGER");service.action(id,"RESUME",action(id));assertThat(state(id)).isEqualTo("SUBMITTED");service.action(id,"REJECT",action(id));
        as(preparer,"STORE_MANAGER");assertThat(service.detail(id)).isNotNull();String ref=((Snapshot)service.detail(id).get("snapshot")).invoice().invoiceReference();
        assertThatThrownBy(()->service.write(null,write(ref))).hasMessageContaining("already exists");assertThatThrownBy(()->service.action(id,"SUBMIT",action(id))).isInstanceOf(RuntimeException.class);
        as(director,"DIRECTOR");assertThatThrownBy(()->service.detail(id)).isInstanceOf(ResourceNotFoundException.class);
    }
    @Test void selfReviewAfterRoleChangeAndStageSkipping()throws Exception {
        UUID id=create();submit(id);as(preparer,"ACCOUNTS_MANAGER");assertThatThrownBy(()->service.action(id,"VERIFY",action(id))).hasMessageContaining("preparer");
        as(director,"DIRECTOR");assertThatThrownBy(()->service.action(id,"APPROVE",action(id))).isInstanceOf(RuntimeException.class);
        as(manager,"ACCOUNTS_MANAGER");assertThatThrownBy(()->service.action(id,"APPROVE",action(id))).isInstanceOf(RuntimeException.class);service.action(id,"VERIFY",action(id));
        as(preparer,"DIRECTOR");assertThatThrownBy(()->service.action(id,"APPROVE",action(id))).hasMessageContaining("preparer");
    }
    @Test void revisionsAndEvidenceRemainHistoricalAndAuthorized()throws Exception {
        UUID id=create();upload(id);UUID old=(UUID)((List<Map<String,Object>>)service.detail(id).get("evidence")).getFirst().get("id");
        service.upload(id,action(id),"replacement.png","image/png",true,old,png());
        assertThat(service.history(id,"evidence",0,10).items()).hasSize(2);assertThat(service.history(id,"revisions",0,10).items()).hasSize(3);assertThat(service.document(id,old).bytes()).isEqualTo(png());
        UUID otherBill=create();assertThatThrownBy(()->service.document(otherBill,old)).isInstanceOf(RuntimeException.class);
        as(preparer,"STORE_MANAGER");service.action(id,"SUBMIT",action(id));assertThatThrownBy(()->upload(id)).isInstanceOf(RuntimeException.class);
        as(manager,"ACCOUNTS_MANAGER");service.action(id,"VERIFY",action(id));
        assertThatThrownBy(()->jdbc.update(sql("delete from casino.accounts_evidence where id=?"),old)).isInstanceOf(RuntimeException.class);
    }
    @Test void unavailableEvidenceCannotVerifyAndInvalidUploadsDoNotCount()throws Exception {
        UUID id=create();Files.write(files.resolve("upload-interrupted.incomplete"),new byte[]{1,2});assertThatThrownBy(()->service.upload(id,action(id),"bad.pdf","application/pdf",true,null,"%PDF-bad".getBytes())).isInstanceOf(IllegalArgumentException.class);
        service.action(id,"SUBMIT",action(id));as(manager,"ACCOUNTS_MANAGER");assertThatThrownBy(()->service.action(id,"VERIFY",action(id))).hasMessageContaining("invoice");
        service.action(id,"RETURN",action(id));as(preparer,"STORE_MANAGER");upload(id);service.action(id,"SUBMIT",action(id));
        // Deliberately corrupt a test-only blob. Operational files are never touched.
        try(var paths=Files.list(files)){Files.write(paths.filter(p->!p.toString().endsWith(".incomplete")).findFirst().orElseThrow(),new byte[]{1});}
        as(manager,"ACCOUNTS_MANAGER");assertThatThrownBy(()->service.action(id,"VERIFY",action(id))).hasMessageContaining("unavailable");assertThat(state(id)).isEqualTo("SUBMITTED");
    }
    @Test void createDateReviewAfterCloseReplayAndRollback()throws Exception {
        var request=write("Invoice A");var result=service.write(null,request);when(dates.getCurrentOpenBusinessDate()).thenReturn(Optional.empty());
        assertThat(service.write(null,request)).isEqualTo(result);assertThatThrownBy(()->service.write(null,write("other"))).hasMessageContaining("OPEN");
        submit(result.id());as(manager,"ACCOUNTS_MANAGER");service.action(result.id(),"VERIFY",action(result.id()));as(director,"DIRECTOR");service.action(result.id(),"APPROVE",action(result.id()));
        assertThat(((Map<?,?>)service.detail(result.id()).get("bill")).get("business_date").toString()).isEqualTo(day.toString());
        org.mockito.Mockito.verify(dates,never()).validateNewOperationalMutationAllowed();org.mockito.Mockito.verify(dates,never()).validateSettlementMutationAllowed();
        as(preparer,"STORE_MANAGER");assertThatThrownBy(()->service.write(null,new BillWrite(request.idempotencyKey(),null,day,invoice("different")))).hasMessageContaining("intent");
        doThrow(new IllegalStateException("audit failure")).when(audit).logForBusinessDate(any(),anyString(),eq("ACCOUNTS"),any(),any(),anyString());
        assertThatThrownBy(()->service.party(new PartyWrite(key(),"FAIL","Private reason","OTHER"))).hasMessageContaining("audit failure");
        assertThat(jdbc.queryForObject(sql("select count(*) from casino.accounts_parties where code='FAIL'"),Integer.class)).isZero();
    }
    @Test void duplicateCreateRaceAndConflictingDecisions()throws Exception {
        BillWrite one=write(" INV   001 "),two=write("inv 001");assertThat(race(()->service.write(null,one),()->service.write(null,two),preparer,"STORE_MANAGER")).containsExactlyInAnyOrder(true,false);
        UUID id=create();submit(id);Action frozen=action(id);assertThat(race(()->service.action(id,"VERIFY",frozen),()->service.action(id,"REJECT",new Action(key(),frozen.expectedVersion(),"Reject")),manager,"ACCOUNTS_MANAGER")).containsExactlyInAnyOrder(true,false);
    }
    @Test void sameKeyRaceCompletesOnceAndStaleEvidenceFails()throws Exception {
        var request=write("Replay");assertThat(race(()->service.write(null,request),()->service.write(null,request),preparer,"STORE_MANAGER")).containsOnly(true);
        UUID id=create();var stale=action(id);upload(id);assertThatThrownBy(()->service.action(id,"SUBMIT",stale)).hasMessageContaining("changed");
        assertThat(jdbc.queryForObject(sql("select count(*) from casino.accounts_bills where invoice_reference='Replay'"),Integer.class)).isEqualTo(1);
    }
    @Test void exactAmountsAndWrongDatesRejected() {
        assertThatThrownBy(()->service.write(null,new BillWrite(key(),null,day.minusDays(1),invoice("A")))).hasMessageContaining("changed");
        for(String value:List.of("0.001","-1","1000000000000"))assertThatThrownBy(()->AccountsAmounts.amount(new BigDecimal(value))).isInstanceOf(IllegalArgumentException.class);
        var good=invoice("A");AccountsAmounts.validate(good);
        var wrong=new Invoice(party,"A",day,null,"NPR",good.subtotal(),good.discount(),good.tax(),BigDecimal.ZERO,good.lines(),List.of());assertThatThrownBy(()->service.write(null,new BillWrite(key(),null,day,wrong))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void uploadRollbackLeavesNoAcceptedEvidenceAndRetryRemainsSafe()throws Exception {
        UUID id=create();long before=version(id);var request=action(id);var bytes=png();
        doThrow(new IllegalStateException("audit write failed")).when(audit).logForBusinessDate(any(),eq("ACCOUNTS_EVIDENCE_STORED"),eq("ACCOUNTS"),any(),any(),anyString());
        assertThatThrownBy(()->service.upload(id,request,"invoice.png","image/png",true,null,bytes)).hasMessageContaining("audit write failed");
        assertThat(version(id)).isEqualTo(before);assertThat(service.history(id,"evidence",0,10).items()).isEmpty();
        assertThat(jdbc.queryForObject(sql("select count(*) from casino.accounts_operations where retry_key=?"),Integer.class,request.idempotencyKey())).isZero();
        try(var paths=Files.list(files)){assertThat(paths.count()).isEqualTo(1);}
        doReturn(null).when(audit).logForBusinessDate(any(),eq("ACCOUNTS_EVIDENCE_STORED"),eq("ACCOUNTS"),any(),any(),anyString());
        var receipt=service.upload(id,request,"invoice.png","image/png",true,null,bytes);assertThat(service.upload(id,request,"invoice.png","image/png",true,null,bytes)).isEqualTo(receipt);
        assertThat(service.history(id,"evidence",0,10).items()).hasSize(1);
    }
    @Test void evidenceChangeAndSubmissionCannotBindAStaleRevision()throws Exception {
        UUID id=create();upload(id);var frozen=action(id);var bytes=png();
        assertThat(race(()->service.action(id,"SUBMIT",frozen),()->service.upload(id,new Action(key(),frozen.expectedVersion(),null),"new.png","image/png",true,null,bytes),preparer,"STORE_MANAGER")).containsExactlyInAnyOrder(true,false);
        assertThat(version(id)).isEqualTo(frozen.expectedVersion()+1);
    }
    @Test void allReadSurfacesPreserveDraftPrivacyAndCurrentAccountStatus()throws Exception {
        UUID id=create();upload(id);
        for(String role:List.of("STORE_MANAGER","ACCOUNTANT_HEAD","ACCOUNTS_MANAGER","DIRECTOR")){
            as(other,role);for(String kind:List.of("revisions","decisions","evidence"))assertThatThrownBy(()->service.history(id,kind,0,10)).isInstanceOf(ResourceNotFoundException.class);
        }
        as(preparer,"STORE_MANAGER");current.get().setStatus("INACTIVE");assertThatThrownBy(()->service.detail(id)).isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
    }
    @Test void blankReasonsAndReviewerInvoiceEditsAreRejected()throws Exception {
        UUID id=create();submit(id);as(manager,"ACCOUNTS_MANAGER");
        for(String action:List.of("HOLD","RETURN","REJECT"))assertThatThrownBy(()->service.action(id,action,new Action(key(),version(id),"  "))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.write(id,new BillWrite(key(),version(id),null,invoice("changed")))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test void sourceLinksFreezeReferencesWithoutChangingStockOrHotelAmounts() {
        jdbc.execute(sql("create table casino.store_procurements(id uuid primary key,reference text,quantity integer)"));
        jdbc.execute(sql("create table casino.store_movements(id uuid primary key,reference text,movement_type text,quantity integer)"));
        jdbc.execute(sql("create table customer.hotel_bookings(id uuid primary key,booking_code text,actual_cost numeric)"));
        UUID procurement=UUID.randomUUID(),receipt=UUID.randomUUID(),hotel=UUID.randomUUID();
        jdbc.update(sql("insert into casino.store_procurements values(?,'SP-ORIGINAL',5)"),procurement);
        jdbc.update(sql("insert into casino.store_movements values(?,'SM-ORIGINAL','RECEIPT',5)"),receipt);
        jdbc.update(sql("insert into customer.hotel_bookings values(?,'HOTEL-ORIGINAL',900)"),hotel);
        var i=invoice("Linked");var linked=new Invoice(i.partyId(),i.invoiceReference(),i.invoiceDate(),i.dueDate(),i.currency(),i.subtotal(),i.discount(),i.tax(),i.total(),i.lines(),List.of(new Source("PROCUREMENT",procurement),new Source("RECEIPT",receipt),new Source("HOTEL",hotel)));
        UUID id=service.write(null,new BillWrite(key(),null,day,linked)).id();
        jdbc.update(sql("update customer.hotel_bookings set booking_code='HOTEL-CHANGED' where id=?"),hotel);
        var snapshot=(Snapshot)service.detail(id).get("snapshot");assertThat(snapshot.sourceSnapshots()).extracting(SourceSnapshot::reference).containsExactlyInAnyOrder("SP-ORIGINAL","SM-ORIGINAL","HOTEL-ORIGINAL");
        assertThat(snapshot.invoice().total()).isEqualByComparingTo("113");assertThat(jdbc.queryForObject(sql("select actual_cost from customer.hotel_bookings where id=?"),BigDecimal.class,hotel)).isEqualByComparingTo("900");
        assertThat(jdbc.queryForObject(sql("select quantity from casino.store_procurements where id=?"),Integer.class,procurement)).isEqualTo(5);
        assertThat(service.sources("RECEIPT","SM-",0,10).items()).hasSize(1);
        assertThatThrownBy(()->jdbc.update(sql("update casino.accounts_parties set name='Changed' where id=?"),party)).isInstanceOf(RuntimeException.class);
    }
    @Test void contextReturnsAuthenticatedUuidEvenWithoutOpenBusinessDate() {
        when(dates.getCurrentOpenBusinessDate()).thenReturn(Optional.empty());
        var context=service.context();assertThat(context.get("actorId")).isEqualTo(preparer);assertThat(context.get("role")).isEqualTo("STORE_MANAGER");assertThat(context.get("username")).isEqualTo(preparer.toString());assertThat(context.get("businessDate")).isNull();
    }
    @Test void completedReplayStillRequiresCurrentVisibility()throws Exception {
        UUID id=create();upload(id);Action uploaded=action(id);var bytes=png();service.upload(id,uploaded,"second.png","image/png",true,null,bytes);
        as(preparer,"DIRECTOR");assertThatThrownBy(()->service.upload(id,uploaded,"second.png","image/png",true,null,bytes)).isInstanceOf(ResourceNotFoundException.class);
        as(preparer,"STORE_MANAGER");service.action(id,"SUBMIT",action(id));as(manager,"ACCOUNTS_MANAGER");Action hold=action(id);var done=service.action(id,"HOLD",hold);
        assertThat(service.action(id,"HOLD",hold)).isEqualTo(done);
        as(manager,"STORE_MANAGER");assertThatThrownBy(()->service.action(id,"HOLD",hold)).isInstanceOf(ResourceNotFoundException.class);
    }
    @Test void normalizedReplayRetainsOriginalDisplayAndMoneyUsesStrings()throws Exception {
        var request=write(" Inv   A ");var receipt=service.write(null,request);var original=request.invoice();
        var equivalent=new Invoice(party,"inv a",day,original.dueDate(),"NPR",new BigDecimal("100.100"),new BigDecimal("0.1"),new BigDecimal("13.00"),new BigDecimal("113.0"),original.lines(),List.of());
        assertThat(service.write(null,new BillWrite(request.idempotencyKey(),null,day,equivalent))).isEqualTo(receipt);
        var snap=(Snapshot)service.detail(receipt.id()).get("snapshot");assertThat(snap.invoice().invoiceReference()).isEqualTo(" Inv   A ");
        var mapper=new ObjectMapper().findAndRegisterModules().disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var tree=mapper.readTree(mapper.writeValueAsString(snap));assertThat(tree.at("/invoice/total").isTextual()).isTrue();assertThat(tree.at("/invoice/total").asText()).isEqualTo("113.00");
        assertThat(tree.at("/invoice/lines/0/amount").asText()).isEqualTo("100.10");
        assertThat(service.detail(receipt.id()).get("bill")).isInstanceOf(Map.class);
        var time=repository.row("select timestamp '2026-09-19 10:11:12.123456' as time",Map.of()).get("time");assertThat(time).isEqualTo(LocalDateTime.of(2026,9,19,10,11,12,123456000));
        assertThat(mapper.writeValueAsString(time)).contains(".123456");
    }
    @Test void competingEditSubmitVerificationReturnAndApprovalReturnAreAtomic()throws Exception {
        UUID first=create();upload(first);Action before=action(first);
        assertThat(race(()->service.write(first,new BillWrite(key(),before.expectedVersion(),null,invoice("Corrected"))),()->service.action(first,"SUBMIT",before),preparer,"STORE_MANAGER")).containsExactlyInAnyOrder(true,false);
        UUID second=create();submit(second);Action v=action(second);
        assertThat(race(()->service.action(second,"VERIFY",v),()->service.action(second,"RETURN",new Action(key(),v.expectedVersion(),"Return")),manager,"ACCOUNTS_MANAGER")).containsExactlyInAnyOrder(true,false);
        UUID third=create();verify(third);Action a=action(third);
        assertThat(race(()->service.action(third,"APPROVE",a),()->service.action(third,"RETURN",new Action(key(),a.expectedVersion(),"Return")),director,"DIRECTOR")).containsExactlyInAnyOrder(true,false);
        assertThat(jdbc.queryForObject(sql("select count(*) from casino.accounts_decisions where bill_id=? and action in ('APPROVE','RETURN')"),Integer.class,third)).isEqualTo(1);
    }
    @Test void finalizationCannotChangeSubmittedEvidenceAndLossAfterApprovalDoesNotReverseIt()throws Exception {
        UUID id=create();submit(id);long before=version(id);byte[] bytes=png();
        var start=new CountDownLatch(1);try(var pool=Executors.newFixedThreadPool(2)) {
            var upload=pool.submit(()->{as(preparer,"STORE_MANAGER");start.await();assertThatThrownBy(()->service.upload(id,new Action(key(),before,null),"new.png","image/png",true,null,bytes)).isInstanceOf(RuntimeException.class);return true;});
            var verify=pool.submit(()->{as(manager,"ACCOUNTS_MANAGER");start.await();return service.action(id,"VERIFY",new Action(key(),before,""));});start.countDown();assertThat(upload.get(10,TimeUnit.SECONDS)).isTrue();assertThat(verify.get(10,TimeUnit.SECONDS)).isNotNull();
        }
        as(director,"DIRECTOR");service.action(id,"APPROVE",action(id));var d=service.detail(id);UUID evidence=(UUID)((List<Map<String,Object>>)d.get("evidence")).getFirst().get("id");
        try(var paths=Files.list(files)){Files.delete(paths.findFirst().orElseThrow());}
        assertThatThrownBy(()->service.document(id,evidence)).hasMessageContaining("unavailable");assertThat(state(id)).isEqualTo("APPROVED_FOR_PAYMENT");assertThat(service.detail(id)).isNotNull();
    }
    @Test void hiddenAccountsEventsAreExcludedBeforeAuditPaginationAndSearch()throws Exception {
        jdbc.execute(sql("alter table core.users add column full_name text"));
        jdbc.execute("create table "+schema+".audit_logs(id uuid,business_date date,action_type text,module_name text,entity_id uuid,performed_at timestamp,performed_by uuid,remarks text)");
        for(int n=0;n<3;n++)jdbc.update("insert into "+schema+".audit_logs values(?,?,'ACCOUNTS_CREATED','ACCOUNTS',?,now(),?,'private')",UUID.randomUUID(),day,UUID.randomUUID(),preparer);
        jdbc.update("insert into "+schema+".audit_logs values(?,?,'USER_CREATED','USER_MANAGEMENT',?,now(),?,'role=CASHIER')",UUID.randomUUID(),day,UUID.randomUUID(),preparer);
        var named=new NamedParameterJdbcTemplate(jdbc.getDataSource()) {
            @Override public <T>List<T> query(String q,org.springframework.jdbc.core.namedparam.SqlParameterSource a,RowMapper<T> mapper){return super.query(sql(q).replace("audit.",schema+"."),a,mapper);}
        };
        String countSql=AuditLogRepository.class.getMethod("countByBusinessDate",LocalDate.class).getAnnotation(org.springframework.data.jpa.repository.Query.class).value();
        assertThat(new NamedParameterJdbcTemplate(jdbc.getDataSource()).queryForObject(countSql.replace("audit.",schema+"."),Map.of("businessDate",day),Long.class)).isEqualTo(1);
        var auditReads=new AuditLogReadRepository(named);var visible=auditReads.read(0,1,null,null,null,null,null,null,null);assertThat(visible.items()).hasSize(1);assertThat(visible.hasNext()).isFalse();
        assertThat(auditReads.read(0,50,null,null,null,null,"ACCOUNTS",null,null).items()).isEmpty();assertThat(auditReads.read(0,50,null,null,null,null,null,null,"accounts").items()).isEmpty();
    }
    List<Boolean> race(Callable<?> a,Callable<?> b,UUID actor,String role)throws Exception {
        var start=new CountDownLatch(1);try(var pool=Executors.newFixedThreadPool(2)){
            List<Future<Boolean>> futures=new ArrayList<>();for(var task:List.of(a,b))futures.add(pool.submit(()->{as(actor,role);start.await();try{task.call();return true;}catch(RuntimeException ex){return false;}finally{current.remove();}}));start.countDown();return List.of(futures.get(0).get(15,TimeUnit.SECONDS),futures.get(1).get(15,TimeUnit.SECONDS));
        }
    }
}
