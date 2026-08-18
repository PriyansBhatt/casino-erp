package com.casino.casinoerp;

import com.casino.casinoerp.dto.CreateLosingReturnRequest;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.exception.ResourceConflictException;
import com.casino.casinoerp.repository.*;
import com.casino.casinoerp.security.Role;
import com.casino.casinoerp.service.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LosingReturnServiceTests {
    private final LosingReturnRepository repo=mock(LosingReturnRepository.class); private final CustomerRepository customers=mock(CustomerRepository.class);
    private final CustomerSessionRepository sessions=mock(CustomerSessionRepository.class); private final ChipBuyInRepository buyIns=mock(ChipBuyInRepository.class);
    private final ChipCashOutRepository cashOuts=mock(ChipCashOutRepository.class); private final VerifiedGamingResultRepository results=mock(VerifiedGamingResultRepository.class);
    private final BusinessDateService dates=mock(BusinessDateService.class); private final SystemLockService lock=mock(SystemLockService.class);
    private final AuthenticatedUserService users=mock(AuthenticatedUserService.class); private final CurrentUserRoleService roles=mock(CurrentUserRoleService.class);
    private final CashierReconciliationService reconciliations=mock(CashierReconciliationService.class); private final AuditLogService audit=mock(AuditLogService.class);
    private final LosingReturnService service=new LosingReturnService(repo,customers,sessions,buyIns,cashOuts,results,dates,lock,users,roles,new RolePermissionService(),reconciliations,audit);
    private final UUID customerId=UUID.randomUUID(), sessionId=UUID.randomUUID(), actorId=UUID.randomUUID(); private final LocalDate date=LocalDate.of(2026,8,8);
    @BeforeEach void setup(){ when(roles.getCurrentUserRole()).thenReturn(Role.CASHIER.name()); when(dates.getCurrentBusinessDate()).thenReturn(date);
        Customer c=new Customer(); c.setId(customerId); c.setStatus(CustomerStatus.ACTIVE); when(customers.findById(customerId)).thenReturn(Optional.of(c));
        CustomerSession s=new CustomerSession(); s.setId(sessionId); s.setCustomerId(customerId); s.setStatus("OPEN"); s.setBusinessDate(date); when(sessions.findFirstByCustomerIdAndStatusIgnoreCase(customerId,"OPEN")).thenReturn(Optional.of(s));
        User u=new User(); u.setId(actorId); when(users.getRequiredUser()).thenReturn(u); when(repo.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(repo.findByCustomerIdAndBusinessDateOrderByCreatedAtAsc(customerId,date)).thenReturn(List.of()); when(buyIns.findByCustomerIdAndBusinessDate(customerId,date)).thenReturn(List.of());
        when(cashOuts.findByCustomerIdAndBusinessDate(customerId,date)).thenReturn(List.of()); when(results.findByCustomerIdAndBusinessDateOrderByCreatedAtAsc(customerId,date)).thenReturn(List.of());
        when(repo.save(any())).thenAnswer(i->{ LosingReturn v=i.getArgument(0); v.setId(UUID.randomUUID()); return v; }); }
    @Test void netVerifiedLossSubtractsWinsFromLosses(){ when(results.findByCustomerIdAndBusinessDateOrderByCreatedAtAsc(customerId,date)).thenReturn(List.of(result(VerifiedGamingResultType.WIN,"50000"),result(VerifiedGamingResultType.LOSS,"150000")));
        var value=service.eligibility(customerId); assertThat(value.eligibleVerifiedLoss()).isEqualByComparingTo("100000"); assertThat(value.availableReturnAmount()).isEqualByComparingTo("10000"); }
    @Test void lossBelowMinimumIsNotEligible(){ when(results.findByCustomerIdAndBusinessDateOrderByCreatedAtAsc(customerId,date)).thenReturn(List.of(result(VerifiedGamingResultType.LOSS,"19999")));
        var value=service.eligibility(customerId); assertThat(value.eligible()).isFalse(); assertThat(value.availableReturnAmount()).isZero(); assertThat(value.minimumEligibleLoss()).isEqualByComparingTo("20000"); assertThat(value.eligibilityReason()).contains("NPR 20,000"); }
    @Test void exactMinimumIsEligible(){ when(results.findByCustomerIdAndBusinessDateOrderByCreatedAtAsc(customerId,date)).thenReturn(List.of(result(VerifiedGamingResultType.LOSS,"20000")));
        var value=service.eligibility(customerId); assertThat(value.eligible()).isTrue(); assertThat(value.availableReturnAmount()).isEqualByComparingTo("2000"); }
    @Test void winsCanReduceGrossLossBelowMinimum(){ when(results.findByCustomerIdAndBusinessDateOrderByCreatedAtAsc(customerId,date)).thenReturn(List.of(result(VerifiedGamingResultType.LOSS,"20000"),result(VerifiedGamingResultType.WIN,"5000")));
        var value=service.eligibility(customerId); assertThat(value.eligibleVerifiedLoss()).isEqualByComparingTo("15000"); assertThat(value.eligible()).isFalse(); }
    @Test void netLossAboveMinimumIsEligible(){ when(results.findByCustomerIdAndBusinessDateOrderByCreatedAtAsc(customerId,date)).thenReturn(List.of(result(VerifiedGamingResultType.LOSS,"30000"),result(VerifiedGamingResultType.WIN,"5000")));
        var value=service.eligibility(customerId); assertThat(value.eligibleVerifiedLoss()).isEqualByComparingTo("25000"); assertThat(value.eligible()).isTrue(); }
    @Test void priorReturnsPreventOverReturn(){ when(results.findByCustomerIdAndBusinessDateOrderByCreatedAtAsc(customerId,date)).thenReturn(List.of(result(VerifiedGamingResultType.LOSS,"100000")));
        LosingReturn prior=new LosingReturn(); prior.setAmountPaid(new BigDecimal("10000")); when(repo.findByCustomerIdAndBusinessDateOrderByCreatedAtAsc(customerId,date)).thenReturn(List.of(prior));
        var value=service.eligibility(customerId); assertThat(value.eligible()).isFalse(); assertThat(value.availableReturnAmount()).isZero(); }
    @Test void createPersistsServerCalculatedAmountAndAudits(){ when(results.findByCustomerIdAndBusinessDateOrderByCreatedAtAsc(customerId,date)).thenReturn(List.of(result(VerifiedGamingResultType.LOSS,"50000")));
        var value=service.create(new CreateLosingReturnRequest(customerId,sessionId,"key",null)); assertThat(value.amountPaid()).isEqualByComparingTo("5000"); assertThat(value.paymentMode()).isEqualTo("CASH");
        verify(reconciliations).validatePostingAllowed(actorId,date); verify(audit).log(eq("CREATE_LOSING_RETURN"),any(),any(),eq(actorId),any()); }
    @Test void submittedReconciliationBlocksBeforePersistence(){ when(results.findByCustomerIdAndBusinessDateOrderByCreatedAtAsc(customerId,date)).thenReturn(List.of(result(VerifiedGamingResultType.LOSS,"50000")));
        doThrow(new ResourceConflictException("Cashier reconciliation has already been submitted for this Business Date.")).when(reconciliations).validatePostingAllowed(actorId,date);
        assertThatThrownBy(()->service.create(new CreateLosingReturnRequest(customerId,sessionId,"key",null))).hasMessageContaining("reconciliation"); verify(repo,never()).save(any()); }
    @Test void directPostBelowThresholdIsRejected(){ when(results.findByCustomerIdAndBusinessDateOrderByCreatedAtAsc(customerId,date)).thenReturn(List.of(result(VerifiedGamingResultType.LOSS,"19999")));
        assertThatThrownBy(()->service.create(new CreateLosingReturnRequest(customerId,sessionId,"below",null))).hasMessageContaining("no eligible verified net loss"); verify(repo,never()).save(any()); }
    @Test void reopenedReconciliationAllowsPosting(){ when(results.findByCustomerIdAndBusinessDateOrderByCreatedAtAsc(customerId,date)).thenReturn(List.of(result(VerifiedGamingResultType.LOSS,"20000")));
        assertThatNoException().isThrownBy(()->service.create(new CreateLosingReturnRequest(customerId,sessionId,"key",null))); }
    @Test void unauthorizedRoleRejected(){ when(roles.getCurrentUserRole()).thenReturn(Role.RECEPTIONIST.name()); assertThatThrownBy(()->service.eligibility(customerId)).hasMessageContaining("Access denied"); }
    @Test void identicalIdempotentReplayDoesNotWriteAgain(){ LosingReturn prior=new LosingReturn(); prior.setId(UUID.randomUUID()); prior.setCustomerId(customerId); prior.setCustomerSessionId(sessionId); prior.setLosingReturnCode("LR-1"); prior.setBusinessDate(date); prior.setEligibleVerifiedLoss(new BigDecimal("20000")); prior.setReturnRate(new BigDecimal("0.10")); prior.setAmountPaid(new BigDecimal("2000")); prior.setPaymentMode("CASH");
        when(repo.findByIdempotencyKey("replay")).thenReturn(Optional.of(prior)); var value=service.create(new CreateLosingReturnRequest(customerId,sessionId,"replay",null)); assertThat(value.id()).isEqualTo(prior.getId()); verify(repo,never()).save(any()); }
    private VerifiedGamingResult result(VerifiedGamingResultType type,String amount){ VerifiedGamingResult v=new VerifiedGamingResult(); v.setResultType(type); v.setAmount(new BigDecimal(amount)); return v; }
}
