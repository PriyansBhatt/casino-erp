package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.exception.*;
import com.casino.casinoerp.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.*;
import java.util.*;

@Service
public class LosingReturnService {
    private static final BigDecimal RATE = new BigDecimal("0.10");
    private static final BigDecimal MINIMUM_ELIGIBLE_LOSS = new BigDecimal("20000");
    private static final String MINIMUM_REASON = "Minimum eligible verified loss of NPR 20,000 is required.";
    private final LosingReturnRepository repository;
    private final CustomerRepository customers;
    private final CustomerSessionRepository sessions;
    private final ChipBuyInRepository buyIns;
    private final ChipCashOutRepository cashOuts;
    private final VerifiedGamingResultRepository results;
    private final BusinessDateService businessDates;
    private final SystemLockService systemLock;
    private final AuthenticatedUserService authenticatedUsers;
    private final CurrentUserRoleService currentRoles;
    private final RolePermissionService permissions;
    private final CashierReconciliationService reconciliations;
    private final AuditLogService audit;

    public LosingReturnService(LosingReturnRepository repository, CustomerRepository customers,
            CustomerSessionRepository sessions, ChipBuyInRepository buyIns, ChipCashOutRepository cashOuts,
            VerifiedGamingResultRepository results, BusinessDateService businessDates,
            SystemLockService systemLock, AuthenticatedUserService authenticatedUsers,
            CurrentUserRoleService currentRoles, RolePermissionService permissions,
            CashierReconciliationService reconciliations, AuditLogService audit) {
        this.repository=repository; this.customers=customers; this.sessions=sessions; this.buyIns=buyIns;
        this.cashOuts=cashOuts; this.results=results; this.businessDates=businessDates;
        this.systemLock=systemLock; this.authenticatedUsers=authenticatedUsers; this.currentRoles=currentRoles;
        this.permissions=permissions; this.reconciliations=reconciliations; this.audit=audit;
    }

    @Transactional(readOnly=true)
    public LosingReturnEligibilityResponse eligibility(UUID customerId) {
        validateViewRole();
        LocalDate date = currentDate();
        CustomerSession session = activeSession(customerId, date);
        return calculate(customerId, session.getId(), date);
    }

    @Transactional(readOnly=true)
    public List<LosingReturnHistoryResponse> history(UUID customerId, LocalDate businessDate) {
        validateViewRole();
        return repository.findHistory(customerId, businessDate);
    }

    @Transactional
    public LosingReturnResponse create(CreateLosingReturnRequest request) {
        validateRole();
        String key = request.idempotencyKey().trim();
        LosingReturn replay = repository.findByIdempotencyKey(key).orElse(null);
        if (replay != null) {
            if (!replay.getCustomerId().equals(request.customerId()) || !replay.getCustomerSessionId().equals(request.customerSessionId()))
                throw new ResourceConflictException("Idempotency key has already been used for a different Losing Return.");
            return response(replay);
        }
        businessDates.validateSettlementMutationAllowed();
        LocalDate date = currentDate();
        if (systemLock.isSystemLocked()) throw new RuntimeException("System is locked. Losing Return transactions are not allowed.");
        User actor = authenticatedUsers.getRequiredUser();
        reconciliations.validatePostingAllowed(actor.getId(), date);
        CustomerSession session = lockedActiveSession(
                request.customerId(), request.customerSessionId(), date);
        replay = repository.findByIdempotencyKey(key).orElse(null);
        if (replay != null) {
            if (!replay.getCustomerId().equals(request.customerId())
                    || !replay.getCustomerSessionId().equals(request.customerSessionId()))
                throw new ResourceConflictException(
                        "Idempotency key has already been used for a different Losing Return.");
            return response(replay);
        }
        LosingReturnEligibilityResponse eligible = calculate(request.customerId(), session.getId(), date);
        if (eligible.alreadyPaid())
            throw new ResourceConflictException("A Losing Return has already been posted for this customer and Business Date.");
        if (!eligible.eligible() || eligible.availableReturnAmount().signum() <= 0)
            throw new ResourceConflictException("Customer has no eligible verified net loss for a Losing Return.");
        LosingReturn value = new LosingReturn();
        value.setLosingReturnCode("LR-" + date.toString().replace("-", "") + "-" + UUID.randomUUID());
        value.setCustomerId(request.customerId()); value.setCustomerSessionId(session.getId()); value.setBusinessDate(date);
        value.setEligibleVerifiedLoss(eligible.eligibleVerifiedLoss()); value.setReturnRate(RATE);
        value.setAmountPaid(eligible.availableReturnAmount()); value.setPaymentMode("CASH"); value.setIdempotencyKey(key);
        value.setCreatedAt(LocalDateTime.now()); value.setCreatedBy(actor.getId()); value.setRemarks(normalize(request.remarks()));
        LosingReturn saved = repository.save(value);
        audit.log("CREATE_LOSING_RETURN", "LOSING_RETURN", saved.getId(), actor.getId(),
                "Losing Return " + saved.getLosingReturnCode() + ", businessDate=" + date
                        + ", eligibleLoss=" + saved.getEligibleVerifiedLoss() + ", paid=" + saved.getAmountPaid());
        return response(saved);
    }

    private LosingReturnEligibilityResponse calculate(UUID customerId, UUID sessionId, LocalDate date) {
        BigDecimal buyIn = buyIns.findByCustomerIdAndBusinessDate(customerId,date).stream().map(ChipBuyIn::getTotalChipValueIssued).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal cashOut = cashOuts.findByCustomerIdAndBusinessDate(customerId,date).stream().map(ChipCashOut::getTotalChipValueReturned).reduce(BigDecimal.ZERO,BigDecimal::add);
        List<VerifiedGamingResult> gaming = results.findByCustomerIdAndBusinessDateOrderByCreatedAtAsc(customerId,date);
        BigDecimal wins = gaming.stream().filter(v->v.getResultType()==VerifiedGamingResultType.WIN).map(VerifiedGamingResult::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal losses = gaming.stream().filter(v->v.getResultType()==VerifiedGamingResultType.LOSS).map(VerifiedGamingResult::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        var priorReturns = repository.findByCustomerIdAndBusinessDateOrderByCreatedAtAsc(customerId,date);
        boolean alreadyPaid = !priorReturns.isEmpty();
        BigDecimal prior = priorReturns.stream().map(LosingReturn::getAmountPaid).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal netLoss = losses.subtract(wins).max(BigDecimal.ZERO);
        boolean meetsMinimum = netLoss.compareTo(MINIMUM_ELIGIBLE_LOSS) >= 0;
        BigDecimal available = meetsMinimum
                ? netLoss.multiply(RATE).setScale(2, RoundingMode.HALF_UP).subtract(prior).max(BigDecimal.ZERO)
                : BigDecimal.ZERO;
        boolean eligible = !alreadyPaid && meetsMinimum && available.signum() > 0;
        String reason = alreadyPaid ? "A Losing Return has already been paid for this customer and Business Date. No further payout is permitted." : !meetsMinimum ? MINIMUM_REASON
                : available.signum() == 0 ? "The available Losing Return has already been paid." : "Eligible verified net loss meets the minimum requirement.";
        return new LosingReturnEligibilityResponse(customerId,sessionId,date,buyIn,wins,losses,cashOut,prior,
                netLoss,MINIMUM_ELIGIBLE_LOSS,RATE,available,eligible,reason,alreadyPaid);
    }
    private CustomerSession activeSession(UUID customerId, LocalDate date) {
        Customer customer=customers.findById(customerId).orElseThrow(()->new ResourceNotFoundException("Customer not found."));
        if (customer.getStatus()!=CustomerStatus.ACTIVE) throw new IllegalArgumentException("Customer must be ACTIVE.");
        CustomerSession session=sessions.findFirstByCustomerIdAndStatusIgnoreCaseAndExitTimeIsNull(customerId,"OPEN").orElseThrow(()->new ResourceNotFoundException("Active customer session not found."));
        if (!customerId.equals(session.getCustomerId()) || !"OPEN".equalsIgnoreCase(session.getStatus()) || session.getExitTime() != null)
            throw new IllegalArgumentException("Customer session must be owned by the customer, OPEN and unexited.");
        if (!date.equals(session.getBusinessDate())) throw new IllegalArgumentException("Customer session does not belong to the current OPEN Business Date.");
        return session;
    }
    private CustomerSession lockedActiveSession(UUID customerId, UUID sessionId, LocalDate date) {
        Customer customer=customers.findById(customerId).orElseThrow(()->new ResourceNotFoundException("Customer not found."));
        if (customer.getStatus()!=CustomerStatus.ACTIVE) throw new IllegalArgumentException("Customer must be ACTIVE.");
        CustomerSession session=sessions.findByIdForUpdate(sessionId).orElseThrow(()->new ResourceNotFoundException("Active customer session not found."));
        if (!customerId.equals(session.getCustomerId())) throw new IllegalArgumentException("Customer session does not belong to the supplied customer.");
        if (!"OPEN".equalsIgnoreCase(session.getStatus()) || session.getExitTime() != null) throw new IllegalArgumentException("Customer session must be OPEN and unexited.");
        if (!date.equals(session.getBusinessDate())) throw new IllegalArgumentException("Customer session does not belong to the current OPEN Business Date.");
        return session;
    }
    private void validateRole(){ if(!permissions.canLosingReturn(currentRoles.getCurrentUserRole())) throw new RuntimeException("Access denied. Only Cashier or Super Admin can use Losing Return."); }
    private void validateViewRole(){ if(!permissions.canViewLosingReturn(currentRoles.getCurrentUserRole())) throw new RuntimeException("Access denied. Losing Return eligibility is restricted."); }
    private LocalDate currentDate(){ businessDates.validateBusinessDateIsOpen(); return businessDates.getCurrentBusinessDate(); }
    private String normalize(String v){ return v==null||v.isBlank()?null:v.trim(); }
    private LosingReturnResponse response(LosingReturn v){ return new LosingReturnResponse(v.getId(),v.getLosingReturnCode(),v.getCustomerId(),v.getCustomerSessionId(),v.getBusinessDate(),v.getEligibleVerifiedLoss(),v.getReturnRate(),v.getAmountPaid(),v.getPaymentMode(),v.getCreatedAt(),v.getRemarks()); }
}
