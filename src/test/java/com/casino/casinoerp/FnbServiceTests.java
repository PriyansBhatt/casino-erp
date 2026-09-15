package com.casino.casinoerp;

import com.casino.casinoerp.dto.FnbDtos.*;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.repository.*;
import com.casino.casinoerp.service.*;
import com.casino.casinoerp.security.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class FnbServiceTests {
 FnbRepository repo=mock(FnbRepository.class);CustomerRepository customers=mock(CustomerRepository.class);
 CustomerSessionRepository sessions=mock(CustomerSessionRepository.class);BusinessDateService dates=mock(BusinessDateService.class);
 SystemLockService locks=mock(SystemLockService.class);CurrentUserRoleService roles=mock(CurrentUserRoleService.class);
 AuthenticatedUserService users=mock(AuthenticatedUserService.class);AuditLogService audit=mock(AuditLogService.class);
 FnbService service=new FnbService(repo,customers,sessions,dates,locks,roles,users,audit,new ObjectMapper().findAndRegisterModules());
 UUID customerId=UUID.randomUUID(),actorId=UUID.randomUUID(),id=UUID.randomUUID();LocalDate date=LocalDate.of(2026,9,15);
 @BeforeEach void setup(){when(repo.replay(anyString())).thenReturn(null);when(roles.getCurrentRole()).thenReturn(Optional.of(Role.SUPER_ADMIN));User actor=new User();actor.setId(actorId);when(users.getRequiredUser()).thenReturn(actor);
 BusinessDate bd=new BusinessDate();bd.setBusinessDate(date);bd.setStatus("OPEN");when(dates.getCurrentOpenBusinessDate()).thenReturn(Optional.of(bd));when(dates.currentCasinoDateTime()).thenReturn(date.atTime(10,0));
 Customer c=new Customer();c.setId(customerId);c.setStatus(CustomerStatus.ACTIVE);when(customers.findById(customerId)).thenReturn(Optional.of(c));when(repo.create(any(),any(),any(),any(),any())).thenReturn(id);}
 Create request(Type type,UUID session,LocalDate expected){return new Create(customerId,session,expected,type,"Rice",2,"Floor",null,"key");}
 @ParameterizedTest @EnumSource(Type.class) void validTypesUseAuthoritativeDateAndActor(Type type){assertThat(service.create(request(type,null,date)).get("id")).isEqualTo(id);
 verify(dates).validateNewOperationalMutationAllowed();verify(repo).create(eq(request(type,null,date)),eq(actorId),eq(date),eq(date.atTime(10,0)),anyString());verify(audit).log(eq("CREATE_FNB_REQUEST"),eq("FNB_REQUEST"),eq(id),eq(actorId),anyString());}
 @Test void staleAndMissingDateRejected(){assertThatThrownBy(()->service.create(request(Type.FOOD,null,date.minusDays(1)))).isInstanceOf(RuntimeException.class);when(dates.getCurrentOpenBusinessDate()).thenReturn(Optional.empty());assertThatThrownBy(()->service.create(request(Type.FOOD,null,date))).isInstanceOf(RuntimeException.class);verify(repo,never()).create(any(),any(),any(),any(),any());assertThat(service.overview().available()).isFalse();assertThat(service.overview().metrics()).isNull();}
 @Test void missingAndInactiveCustomerRejected(){when(customers.findById(customerId)).thenReturn(Optional.empty());assertThatThrownBy(()->service.create(request(Type.FOOD,null,date))).isInstanceOf(IllegalArgumentException.class);Customer c=new Customer();when(customers.findById(customerId)).thenReturn(Optional.of(c));assertThatThrownBy(()->service.create(request(Type.FOOD,null,date))).isInstanceOf(IllegalArgumentException.class);}
 @Test void optionalSessionOwnership(){UUID sid=UUID.randomUUID();CustomerSession s=new CustomerSession();s.setCustomerId(UUID.randomUUID());when(sessions.findById(sid)).thenReturn(Optional.of(s));assertThatThrownBy(()->service.create(request(Type.FOOD,sid,date))).isInstanceOf(IllegalArgumentException.class);s.setCustomerId(customerId);s.setStatus("CLOSED");service.create(request(Type.FOOD,sid,date));}
 @Test void lifecycleAndSystemLockFailClosed(){when(locks.isSystemLocked()).thenReturn(true);assertThatThrownBy(()->service.create(request(Type.FOOD,null,date))).isInstanceOf(RuntimeException.class);verify(repo,never()).create(any(),any(),any(),any(),any());}
 @Test void retryRequiresExactActorAndPayload()throws Exception{Create r=request(Type.FOOD,null,date);String signature=actorId+":"+new ObjectMapper().findAndRegisterModules().writeValueAsString(r);when(repo.replay("key")).thenReturn(Map.of("id",id,"request_signature",signature));assertThat(service.create(r).get("replayed")).isEqualTo(true);verify(dates,never()).validateNewOperationalMutationAllowed();when(repo.replay("key")).thenReturn(Map.of("id",id,"request_signature","other"));assertThatThrownBy(()->service.create(r)).isInstanceOf(RuntimeException.class);}
 @Test void everyTransitionIsAuthoritative(){for(Status from:Status.values())for(Status to:Status.values()){
 when(repo.lock(id)).thenReturn(Map.of("status",from.name(),"version",0));Change r=new Change(to,0);
 boolean valid=(from==Status.PENDING&&(to==Status.PREPARING||to==Status.CANCELLED))||(from==Status.PREPARING&&(to==Status.READY||to==Status.CANCELLED))||(from==Status.READY&&(to==Status.DELIVERED||to==Status.CANCELLED));
 if(valid)assertThatCode(()->service.change(id,r)).doesNotThrowAnyException();else assertThatThrownBy(()->service.change(id,r)).isInstanceOf(RuntimeException.class);
 }}
 @Test void staleVersionRejectedAndConfirmedStatusReplayed(){when(repo.lock(id)).thenReturn(Map.of("status","PREPARING","version",1));assertThatThrownBy(()->service.change(id,new Change(Status.CANCELLED,0))).isInstanceOf(RuntimeException.class);when(repo.replayChange(id,new Change(Status.PREPARING,0),actorId)).thenReturn(true);assertThat(service.change(id,new Change(Status.PREPARING,0)).get("replayed")).isEqualTo(true);verify(repo,never()).change(any(),any(),any(),any(),any());}
 @Test void directorReadOnlyAndOtherRolesDenied(){when(roles.getCurrentRole()).thenReturn(Optional.of(Role.DIRECTOR));service.overview();assertThatThrownBy(()->service.create(request(Type.FOOD,null,date))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);when(roles.getCurrentRole()).thenReturn(Optional.of(Role.CASHIER));assertThatThrownBy(()->service.overview()).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);}
 @Test void historyPassesPersistedDateAndBoundedRepository(){service.history(date,null,null,"FOOD","","C1",false);verify(repo).history(date,null,null,"FOOD","","C1",false);assertThat(service.history(null,null,null,"","","",true).limit()).isEqualTo(100);}
 @Test void noFinancialDependencies(){assertThat(Arrays.stream(FnbService.class.getDeclaredFields()).map(f->f.getType().getSimpleName())).noneMatch(n->n.matches(".*(CashOut|BuyIn|LosingReturn|Reconciliation|ChipCustody|Accounting).*"));}
 @Test void oldRequestCanProgressAfterRolloverAndUsesDeliveryDate() {
  LocalDate next=date.plusDays(1);BusinessDate bd=new BusinessDate();bd.setBusinessDate(next);bd.setStatus("OPEN");
  when(dates.getCurrentOpenBusinessDate()).thenReturn(Optional.of(bd));when(dates.currentCasinoDateTime()).thenReturn(next.atTime(10,0));
  when(repo.lock(id)).thenReturn(Map.of("status","READY","version",2));Change delivery=new Change(Status.DELIVERED,2);
  service.change(id,delivery);
  verify(dates).validateSettlementMutationAllowed();verify(repo).change(id,delivery,actorId,next.atTime(10,0),next);
  verify(dates,never()).validateNewOperationalMutationAllowed();
 }
 @Test void historicalReadNeedsNoOpenDateAndMissingDateBlocksMutation() {
  when(dates.getCurrentOpenBusinessDate()).thenReturn(Optional.empty());
  var page=service.history(date,null,null,"","","",false);
  assertThat(page.currentBusinessDate()).isNull();assertThat(page.businessDateStatus()).isEqualTo("UNAVAILABLE");
  verify(repo).history(date,null,null,"","","",false);
  when(repo.lock(id)).thenReturn(Map.of("status","READY","version",2));
  doThrow(new com.casino.casinoerp.exception.ResourceConflictException("No OPEN date")).when(dates).validateSettlementMutationAllowed();
  assertThatThrownBy(()->service.change(id,new Change(Status.DELIVERED,2))).isInstanceOf(com.casino.casinoerp.exception.ResourceConflictException.class);
  verify(repo,never()).change(any(),any(),any(),any(),any());
 }
}
