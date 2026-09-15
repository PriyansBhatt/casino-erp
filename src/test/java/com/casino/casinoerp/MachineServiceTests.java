package com.casino.casinoerp;

import com.casino.casinoerp.dto.MachineDtos.*;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.repository.*;
import com.casino.casinoerp.security.Role;
import com.casino.casinoerp.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.dao.DataIntegrityViolationException;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;

class MachineServiceTests {
    MachineRepository repo=mock(MachineRepository.class);
    BusinessDateRepository datesRepo=mock(BusinessDateRepository.class);
    BusinessDateService dates=mock(BusinessDateService.class);
    SystemLockService locks=mock(SystemLockService.class);
    CustomerRepository customers=mock(CustomerRepository.class);
    CustomerSessionRepository sessions=mock(CustomerSessionRepository.class);
    CurrentUserRoleService roles=mock(CurrentUserRoleService.class);
    AuthenticatedUserService users=mock(AuthenticatedUserService.class);
    AuditLogService audit=mock(AuditLogService.class);
    MachineService service=new MachineService(repo,datesRepo,dates,locks,customers,sessions,roles,users,audit);
    UUID machine=UUID.randomUUID(),customer=UUID.randomUUID(),session=UUID.randomUUID(),actor=UUID.randomUUID(),play=UUID.randomUUID();
    LocalDate date=LocalDate.of(2026,9,15);
    Start input=new Start(customer,session,date,"start");
    CustomerSession visit;
    @BeforeEach void setup() {
        when(roles.getCurrentRole()).thenReturn(Optional.of(Role.SUPER_ADMIN));
        when(dates.getCurrentBusinessDate()).thenReturn(date);
        when(dates.currentCasinoDateTime()).thenReturn(date.atTime(10,0));
        User u=new User();u.setId(actor);when(users.getRequiredUser()).thenReturn(u);
        Customer c=new Customer();c.setId(customer);c.setStatus(CustomerStatus.ACTIVE);when(customers.findById(customer)).thenReturn(Optional.of(c));
        visit=new CustomerSession();visit.setId(session);visit.setCustomerId(customer);visit.setBusinessDate(date);visit.setStatus("OPEN");
        when(sessions.findByIdForUpdate(session)).thenReturn(Optional.of(visit));
        when(repo.lock(machine)).thenReturn(Optional.of(new MachineRepository.StoredMachine(machine,"SLOT","AVAILABLE")));
        when(repo.detail(any())).thenAnswer(call->Optional.of(new Machine(call.getArgument(0),"S1","Slot",Type.SLOT,null,"AVAILABLE",date.atStartOfDay(),date.atStartOfDay(),null)));
    }
    @Test void startUsesServerDateAndOnlyOccupancyWrites() {
        var result=service.start(machine,input);
        assertThat(result.businessDate()).isEqualTo(date);
        verify(repo).start(eq(result.playId()),eq(machine),eq(input),eq(actor),eq(date),any());
        verify(sessions,never()).save(any());verify(dates).validateNewOperationalMutationAllowed();
    }
    @Test void exitedSessionRejected() { visit.setExitTime(date.atTime(10,0));reject("unexited"); }
    @Test void closedSessionRejected() { visit.setStatus("CLOSED");reject("unexited"); }
    @Test void inactiveCustomerRejected() { customers.findById(customer).orElseThrow().setStatus(CustomerStatus.INACTIVE);reject("ACTIVE"); }
    @Test void wrongOwnerRejected() { visit.setCustomerId(UUID.randomUUID());reject("belong"); }
    @Test void oldVisitRejected() { visit.setBusinessDate(date.minusDays(1));reject("Business Date"); }
    @Test void rolloverBeforeAnyMutation() {
        when(dates.getCurrentBusinessDate()).thenReturn(date.plusDays(1));reject("Business Date changed");
        verify(repo,never()).lockCustomer(any());
    }
    @Test void rouletteRejected() { when(repo.lock(machine)).thenReturn(Optional.of(new MachineRepository.StoredMachine(machine,"AUTOMATIC_ROULETTE","AVAILABLE")));reject("not configured"); }
    @Test void outOfServiceRejected() { when(repo.lock(machine)).thenReturn(Optional.of(new MachineRepository.StoredMachine(machine,"SLOT","OUT_OF_SERVICE")));reject("unavailable"); }
    @Test void occupiedMachineOrSessionRejected() { when(repo.occupied(machine,session)).thenReturn(true);reject("occupied"); }
    @Test void databaseConflictControlled() { doThrow(new DataIntegrityViolationException("unique")).when(repo).start(any(),any(),any(),any(),any(),any()); assertThatThrownBy(()->service.start(machine,input)).hasMessageContaining("concurrently"); }
    @Test void systemLockRejectsStartAndEnd() {
        when(locks.isSystemLocked()).thenReturn(true);reject("locked");
        assertThatThrownBy(()->service.end(machine,play,new End(date,"end"))).hasMessageContaining("locked");
        verify(repo,never()).end(any(),any(),any(),any());
    }
    @Test void startReplayRetainsDateAfterRollover() {
        when(repo.byKey("start",false)).thenReturn(Optional.of(stored("ENDED")));
        when(dates.getCurrentBusinessDate()).thenReturn(date.plusDays(1));
        assertThat(service.start(machine,input).playId()).isEqualTo(play);
        verify(repo,never()).start(any(),any(),any(),any(),any(),any());verify(dates,never()).validateNewOperationalMutationAllowed();
    }
    @Test void changedReplayPayloadRejected() {
        when(repo.byKey("start",false)).thenReturn(Optional.of(stored("ACTIVE")));
        assertThatThrownBy(()->service.start(machine,new Start(UUID.randomUUID(),session,date,"start"))).hasMessageContaining("different request");
    }
    @Test void endOnlyClosesPlayWithoutCasinoExitOrFinance() {
        var saved=stored("ACTIVE");when(repo.play(play)).thenReturn(Optional.of(saved));
        service.end(machine,play,new End(date,"end"));
        verify(repo).end(eq(saved),any(),eq(actor),any());verifyNoInteractions(sessions,customers);
        verify(dates).validateSettlementMutationAllowed();
    }
    @Test void wrongOriginalEndDateFailsClosed() { when(repo.play(play)).thenReturn(Optional.of(stored("ACTIVE")));assertThatThrownBy(()->service.end(machine,play,new End(date.minusDays(1),"end"))).hasMessageContaining("Business Date"); }
    @Test void endAfterRolloverPreservesOriginalDateAndOnlyEndsAssignment() {
        when(dates.getCurrentBusinessDate()).thenReturn(date.plusDays(1));
        when(dates.currentCasinoDateTime()).thenReturn(date.plusDays(1).atTime(10,0));
        var saved=stored("ACTIVE");when(repo.play(play)).thenReturn(Optional.of(saved));
        var receipt=service.end(machine,play,new End(date,"end"));
        assertThat(receipt.businessDate()).isEqualTo(date);
        verify(repo).end(eq(saved),any(),eq(actor),eq(date.plusDays(1).atTime(10,0)));
        verify(dates).validateSettlementMutationAllowed();verify(dates).validateBusinessDateIsOpen();
        verifyNoInteractions(sessions,customers);
    }
    @Test void endStillRejectsMissingOpenDate() {
        doThrow(new com.casino.casinoerp.exception.ResourceConflictException("No OPEN date")).when(dates).validateSettlementMutationAllowed();
        assertThatThrownBy(()->service.end(machine,play,new End(date,"end"))).hasMessageContaining("No OPEN date");
        verify(repo,never()).end(any(),any(),any(),any());
    }
    @Test void completedEndReplaySafe() {
        when(repo.byKey("end",true)).thenReturn(Optional.of(stored("ENDED")));
        service.end(machine,play,new End(date,"end"));verify(repo,never()).end(any(),any(),any(),any());
    }
    @Test void occupiedMachineCannotChangeAvailability() {
        when(repo.occupied(machine,machine)).thenReturn(true);
        assertThatThrownBy(()->service.status(machine,new ChangeStatus(Availability.OUT_OF_SERVICE,Availability.AVAILABLE,date))).hasMessageContaining("End active play");
        verify(repo,never()).status(any(),any(),any());
    }
    @Test void availabilityTransitionsAndStaleProtection() {
        service.status(machine,new ChangeStatus(Availability.OUT_OF_SERVICE,Availability.AVAILABLE,date));
        verify(repo).status(eq(machine),eq("OUT_OF_SERVICE"),any());
        when(repo.lock(machine)).thenReturn(Optional.of(new MachineRepository.StoredMachine(machine,"SLOT","OUT_OF_SERVICE")));
        service.status(machine,new ChangeStatus(Availability.AVAILABLE,Availability.OUT_OF_SERVICE,date));
        assertThatThrownBy(()->service.status(machine,new ChangeStatus(Availability.OUT_OF_SERVICE,Availability.AVAILABLE,date))).hasMessageContaining("status changed");
    }
    @Test void duplicateCodeControlled() {
        doThrow(new DataIntegrityViolationException("unique")).when(repo).create(any(),any(),any(),any());
        assertThatThrownBy(()->service.create(new Create("S1","Slot",Type.SLOT,null,date))).hasMessageContaining("code already exists");
    }
    @Test void overviewAndHistoryBounded() {
        var open=new BusinessDate();open.setBusinessDate(date);when(datesRepo.findByStatus("OPEN")).thenReturn(List.of(open));
        when(repo.overview()).thenReturn(List.of());assertThat(service.overview().machines()).isEmpty();
        assertThat(service.detail(machine).historyLimit()).isEqualTo(50);
        verify(repo,times(1)).overview();verify(repo,times(1)).history(machine);
    }
    @ParameterizedTest @EnumSource(Role.class) void rolesEnforcedAtService(Role role) {
        when(roles.getCurrentRole()).thenReturn(Optional.of(role));
        if(role==Role.DIRECTOR||role==Role.SUPER_ADMIN) service.overview();
        else assertThatThrownBy(()->service.overview()).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        if(role!=Role.SUPER_ADMIN) assertThatThrownBy(()->service.start(machine,input)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    void reject(String message) { assertThatThrownBy(()->service.start(machine,input)).hasMessageContaining(message);verify(repo,never()).start(any(),any(),any(),any(),any(),any()); }
    MachineRepository.StoredPlay stored(String status) { return new MachineRepository.StoredPlay(play,machine,customer,session,date,status,actor,actor,"start","end"); }
}
