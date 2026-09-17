package com.casino.casinoerp;

import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.entity.User;
import com.casino.casinoerp.repository.*;
import com.casino.casinoerp.service.*;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Explicit disposable database only. Never falls back to application datasource or ST1 evidence. */
class UserDatabaseTests {
 static AnnotationConfigApplicationContext context;
 static JdbcTemplate jdbc; static UserService service; static UserRepository users;
 static final UUID A=UUID.randomUUID(), B=UUID.randomUUID(), C=UUID.randomUUID();
 @Configuration @EnableTransactionManagement
 @EnableJpaRepositories(basePackages="com.casino.casinoerp.repository", includeFilters=@ComponentScan.Filter(type=FilterType.ASSIGNABLE_TYPE,classes={UserRepository.class,StaffProfileRepository.class,AuditLogRepository.class}))
 static class Config {
  @Bean DataSource dataSource(){
   String url=System.getenv("A1_TEST_JDBC_URL"), username=System.getenv("A1_TEST_DB_USER"), password=System.getenv("A1_TEST_DB_PASSWORD");
   if(url==null || !url.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):5432/casino_erp_a1_test"))throw new IllegalStateException("Set A1_TEST_JDBC_URL to the separate local casino_erp_a1_test database.");
   var ds=new DriverManagerDataSource(url,username,password);var j=new JdbcTemplate(ds);
   j.execute("create schema if not exists core");j.execute("create schema if not exists casino");j.execute("create schema if not exists audit");
   j.execute("create table if not exists core.users(id uuid primary key, username varchar(100) not null unique, full_name varchar(150) not null, email varchar(150) unique, password_hash text not null, role varchar(50), status varchar(20), created_at timestamp, updated_at timestamp)");
   j.execute("create table if not exists casino.staff_profiles(id uuid primary key,user_id uuid not null unique references core.users(id),employee_code varchar(50),department_id uuid,job_title_id uuid,employment_status varchar(20),employment_type varchar(20),date_of_joining date,phone varchar(50),reporting_manager_staff_profile_id uuid,remarks varchar(1000),created_at timestamp,updated_at timestamp)");
   j.execute("create table if not exists audit.audit_logs(id uuid primary key,business_date date,action_type varchar(100),module_name varchar(100),entity_id uuid,performed_by uuid,performed_at timestamp,remarks text)");
   return ds;
  }
  @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds){var f=new LocalContainerEntityManagerFactoryBean();f.setDataSource(ds);f.setPackagesToScan("com.casino.casinoerp.entity");f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","none","hibernate.show_sql","false"));return f;}
  @Bean JpaTransactionManager transactionManager(EntityManagerFactory f){return new JpaTransactionManager(f);}
  @Bean AuthenticatedUserService actors(UserRepository r){return new AuthenticatedUserService(r);}
  @Bean CurrentUserRoleService currentRoles(UserRepository r){return new CurrentUserRoleService(r);}
  @Bean AuditLogService audit(AuditLogRepository r,CurrentUserRoleService roles){var dates=mock(BusinessDateService.class);when(dates.getCurrentBusinessDate()).thenReturn(LocalDate.of(2026,9,17));return new AuditLogService(r,dates,new RolePermissionService(),roles);}
  @Bean UserService service(UserRepository r,StaffProfileRepository staff,AuthenticatedUserService actors,AuditLogService audit,EntityManagerFactory f){return new UserService(r,staff,actors,new BCryptPasswordEncoder(),audit,SharedEntityManagerCreator.createSharedEntityManager(f),jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator());}
 }
 @BeforeAll static void start(){context=new AnnotationConfigApplicationContext(Config.class);jdbc=new JdbcTemplate(context.getBean(DataSource.class));service=context.getBean(UserService.class);users=context.getBean(UserRepository.class);}
 @AfterAll static void stop(){if(context!=null){jdbc.execute("drop schema audit cascade");jdbc.execute("drop schema casino cascade");jdbc.execute("drop schema core cascade");context.close();}}
 @BeforeEach void setup(){jdbc.execute("truncate audit.audit_logs,casino.staff_profiles,core.users cascade");insert(A,"adminA","SUPER_ADMIN");insert(B,"adminB","SUPER_ADMIN");insert(C,"cashier","CASHIER");actor("adminA");}
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 static void actor(String username){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(username,null,List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))));}
 static void insert(UUID id,String name,String role){jdbc.update("insert into core.users values(?,?,?,?,?,?,?,?,?)",id,name,name,name+"@example.com",new BCryptPasswordEncoder().encode("old-password"),role,"ACTIVE",java.sql.Timestamp.valueOf("2026-09-17 10:00:00"),java.sql.Timestamp.valueOf("2026-09-17 10:00:00"));}
 List<Boolean> race(Runnable first,Runnable second)throws Exception{
  var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
  try{var futures=new ArrayList<Future<Boolean>>();for(var task:List.of(first,second))futures.add(pool.submit(()->{start.await();try{task.run();return true;}catch(com.casino.casinoerp.exception.ResourceConflictException|org.springframework.security.core.AuthenticationException|org.springframework.security.access.AccessDeniedException e){return false;}finally{SecurityContextHolder.clearContext();}}));start.countDown();return List.of(futures.get(0).get(20,TimeUnit.SECONDS),futures.get(1).get(20,TimeUnit.SECONDS));}finally{pool.shutdownNow();}
 }
 @Test void duplicateUsernameRaceUsesDatabaseUniqueness()throws Exception{
  Runnable create=()->{actor("adminA");service.create(new CreateUserRequest("duplicate","Name",null,"CASHIER",null,"initial-password"));};
  assertThat(race(create,create)).containsExactlyInAnyOrder(true,false);
  assertThat(jdbc.queryForObject("select count(*) from core.users where username='duplicate'",Integer.class)).isEqualTo(1);
  assertThat(jdbc.queryForObject("select count(*) from audit.audit_logs where action_type='USER_CREATED'",Integer.class)).isEqualTo(1);
 }
 @Test void duplicateEmailRaceUsesDatabaseUniqueness()throws Exception{
  assertThat(race(()->{actor("adminA");service.create(new CreateUserRequest("first","Name","same@example.com","CASHIER",null,"initial-password"));},()->{actor("adminB");service.create(new CreateUserRequest("second","Name","same@example.com","DEALER",null,"initial-password"));})).containsExactlyInAnyOrder(true,false);
  assertThat(jdbc.queryForObject("select count(*) from core.users where email='same@example.com'",Integer.class)).isEqualTo(1);
 }
 @Test void concurrentMutualDemotionsRevalidateActorAndKeepAdministrator()throws Exception{
  assertThat(race(()->{actor("adminA");service.changeRole(B,new ChangeUserRoleRequest("DEALER","SUPER_ADMIN"));},()->{actor("adminB");service.changeRole(A,new ChangeUserRoleRequest("DEALER","SUPER_ADMIN"));})).containsExactlyInAnyOrder(true,false);
  assertThat(users.countActiveSuperAdmins()).isEqualTo(1);
 }
 @Test void concurrentMutualDeactivationKeepsAdministrator()throws Exception{
  assertThat(race(()->{actor("adminA");service.changeStatus(B,new ChangeUserStatusRequest("INACTIVE","ACTIVE"));},()->{actor("adminB");service.changeStatus(A,new ChangeUserStatusRequest("INACTIVE","ACTIVE"));})).containsExactlyInAnyOrder(true,false);
  assertThat(users.countActiveSuperAdmins()).isEqualTo(1);
 }
 @Test void accountMutationDrivesS1LoginAndPasswordSemantics(){
  var encoder=new BCryptPasswordEncoder();var jwt=new JwtService("a1-private-test-secret-"+UUID.randomUUID()+UUID.randomUUID());var roles=context.getBean(CurrentUserRoleService.class);var auth=new AuthService(users,roles,jwt,encoder);
  var oldHash=users.findByUsername("cashier").getPasswordHash();var token=jwt.generateToken("cashier","CASHIER");
  service.resetPassword(C,new ResetUserPasswordRequest("new-password"));
  assertThat(users.findByUsername("cashier").getPasswordHash()).isNotEqualTo(oldHash);
  var request=new LoginRequest();request.setUsername("cashier");request.setPassword("old-password");assertThatThrownBy(()->auth.login(request)).isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
  request.setPassword("new-password");assertThat(auth.login(request).getToken()).isNotBlank();
  assertToken(token,jwt,200); // Password-only change intentionally does not revoke an issued JWT.
  actor("adminA");service.changeStatus(C,new ChangeUserStatusRequest("INACTIVE","ACTIVE"));assertToken(token,jwt,401);
  actor("adminA");service.changeStatus(C,new ChangeUserStatusRequest("ACTIVE","INACTIVE"));assertToken(token,jwt,200);
  actor("adminA");service.changeRole(C,new ChangeUserRoleRequest("DEALER","CASHIER"));assertToken(token,jwt,401);
  assertThat(jdbc.queryForList("select remarks from audit.audit_logs",String.class)).noneMatch(s->s.contains("new-password")||s.contains("old-password")||s.contains("$2a$"));
 }
 void assertToken(String token,JwtService jwt,int expected){
  try{var provider=new org.springframework.beans.factory.support.DefaultListableBeanFactory();provider.registerSingleton("users",users);var filter=new com.casino.casinoerp.config.JwtAuthenticationFilter(jwt,new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules(),provider.getBeanProvider(UserRepository.class));var request=new org.springframework.mock.web.MockHttpServletRequest();request.addHeader("Authorization","Bearer "+token);var response=new org.springframework.mock.web.MockHttpServletResponse();filter.doFilter(request,response,(req,res)->{});assertThat(response.getStatus()).isEqualTo(expected);}catch(Exception e){throw new AssertionError(e);}finally{SecurityContextHolder.clearContext();}
 }
 @Test void auditFailureRollsBackMutation(){
  jdbc.execute("alter table audit.audit_logs add constraint reject_test_event check(action_type <> 'USER_PASSWORD_RESET')");
  String hash=users.findByUsername("cashier").getPasswordHash();
  try{assertThatThrownBy(()->service.resetPassword(C,new ResetUserPasswordRequest("replacement"))).isInstanceOf(RuntimeException.class);assertThat(users.findByUsername("cashier").getPasswordHash()).isEqualTo(hash);}finally{jdbc.execute("alter table audit.audit_logs drop constraint reject_test_event");}
 }
}
