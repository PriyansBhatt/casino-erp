package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.exception.ResourceConflictException;
import com.casino.casinoerp.repository.*;
import com.casino.casinoerp.security.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;

@Service
public class CashierReconciliationService {
    private static final Set<Integer> DENOMINATIONS = Set.of(5, 10, 20, 50, 100, 500, 1000);
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999999999999.99");
    private final CashierReconciliationRepository repository;
    private final ChipBuyInRepository buyInRepository;
    private final ChipCashOutRepository cashOutRepository;
    private final BusinessDateService businessDateService;
    private final AuthenticatedUserService authenticatedUserService;
    private final CurrentUserRoleService currentUserRoleService;
    private final RolePermissionService rolePermissionService;
    private final SystemLockService systemLockService;
    private final AuditLogService auditLogService;
    private final UserRepository userRepository;
    private final LosingReturnRepository losingReturnRepository;
    private final CashierOpeningBalanceRepository openingBalanceRepository;

    public CashierReconciliationService(
            CashierReconciliationRepository repository,
            ChipBuyInRepository buyInRepository,
            ChipCashOutRepository cashOutRepository,
            BusinessDateService businessDateService,
            AuthenticatedUserService authenticatedUserService,
            CurrentUserRoleService currentUserRoleService,
            RolePermissionService rolePermissionService,
            SystemLockService systemLockService,
            AuditLogService auditLogService,
            UserRepository userRepository,
            LosingReturnRepository losingReturnRepository,
            CashierOpeningBalanceRepository openingBalanceRepository) {
        this.repository = repository;
        this.buyInRepository = buyInRepository;
        this.cashOutRepository = cashOutRepository;
        this.businessDateService = businessDateService;
        this.authenticatedUserService = authenticatedUserService;
        this.currentUserRoleService = currentUserRoleService;
        this.rolePermissionService = rolePermissionService;
        this.systemLockService = systemLockService;
        this.auditLogService = auditLogService;
        this.userRepository = userRepository;
        this.losingReturnRepository = losingReturnRepository;
        this.openingBalanceRepository = openingBalanceRepository;
    }

    @Transactional(readOnly = true)
    public CashierReconciliationResponse getCurrent() {
        validateViewRole();
        User actor = authenticatedUserService.getRequiredUser();
        LocalDate businessDate = currentOpenBusinessDate();
        CashierReconciliation existing = repository
                .findByCashierUserIdAndBusinessDate(actor.getId(), businessDate).orElse(null);
        return response(actor, businessDate, existing, tenderSnapshot(actor.getId(), businessDate));
    }

    @Transactional(readOnly = true)
    public List<CashierReconciliationResponse> getSubmittedForCurrentBusinessDate() {
        validateReopenRole();
        LocalDate date = currentOpenBusinessDate();
        return repository.findByBusinessDateOrderBySubmittedAtDesc(date).stream()
                .map(value -> response(cashierUser(value.getCashierUserId()), date, value,
                        tenderSnapshot(value.getCashierUserId(), date)))
                .toList();
    }

    @Transactional(readOnly = true)
    public CashierReconciliationResponse preview(CashierReconciliationRequest request) {
        validateSubmitRole();
        User actor = authenticatedUserService.getRequiredUser();
        LocalDate businessDate = currentOpenBusinessDate();
        Map<Integer, Integer> denominations = normalizeDenominations(request.denominations());
        BigDecimal actual = calculateActual(denominations);
        TenderSnapshot tenders = tenderSnapshot(actor.getId(), businessDate);
        BigDecimal opening = requiredOpeningBalance(actor.getId(), businessDate);
        return calculatedResponse(actor, businessDate, null, "PREVIEW", opening, actual,
                denominations, null, request.remarks(), tenders);
    }

    @Transactional
    public CashierReconciliationResponse submit(CashierReconciliationRequest request) {
        validateSubmitRole();
        businessDateService.validateSettlementMutationAllowed();
        if (systemLockService.isSystemLocked()) {
            throw new RuntimeException("System is locked. Cashier reconciliation cannot be submitted.");
        }
        User actor = authenticatedUserService.getRequiredUser();
        LocalDate businessDate = currentOpenBusinessDate();
        Map<Integer, Integer> denominations = normalizeDenominations(request.denominations());
        BigDecimal actual = calculateActual(denominations);
        String key = request.idempotencyKey().trim();

        CashierReconciliation replay = repository.findByIdempotencyKey(key).orElse(null);
        if (replay != null) {
            validateReplay(replay, actor, businessDate, actual, denominations);
            return response(actor, businessDate, replay, tenderSnapshot(actor.getId(), businessDate));
        }
        BigDecimal opening = requiredOpeningBalance(actor.getId(), businessDate);
        CashierReconciliation existing = repository.findByCashierUserIdAndBusinessDate(actor.getId(), businessDate).orElse(null);
        if (existing != null && !"REOPENED".equals(existing.getLifecycleStatus())) {
            throw new ResourceConflictException("Cashier reconciliation has already been submitted for this Business Date.");
        }

        TenderSnapshot tenders = tenderSnapshot(actor.getId(), businessDate);
        BigDecimal expected = opening.add(tenders.cashReceived()).subtract(tenders.cashPaid());
        validateAmount(expected.abs());
        BigDecimal variance = actual.subtract(expected);
        CashierReconciliation entity = existing == null ? new CashierReconciliation() : existing;
        entity.setBusinessDate(businessDate);
        entity.setCashierUserId(actor.getId());
        entity.setOpeningCash(opening);
        entity.setActualClosingCash(actual);
        entity.setExpectedClosingCash(expected);
        entity.setVariance(variance);
        entity.setStatus(variance.signum() == 0 ? "BALANCED" : variance.signum() > 0 ? "OVER" : "SHORT");
        entity.setLifecycleStatus("SUBMITTED");
        if (entity.getDenominations() == null) {
            entity.setDenominations(new LinkedHashMap<>());
        } else {
            entity.getDenominations().clear();
        }
        entity.getDenominations().putAll(denominations);
        entity.setIdempotencyKey(key);
        entity.setSubmittedAt(LocalDateTime.now());
        entity.setRemarks(normalizeRemarks(request.remarks()));
        entity.setReopenedAt(null);
        entity.setReopenedBy(null);
        entity.setReopenReason(null);
        CashierReconciliation saved = repository.save(entity);
        auditLogService.log("RECONCILIATION_SUBMITTED", "CASHIER_RECONCILIATION",
                saved.getId(), actor.getId(), auditDetail(saved, null));
        return response(actor, businessDate, saved, tenders);
    }

    @Transactional
    public CashierReconciliationResponse reopen(UUID reconciliationId, String reason) {
        validateReopenRole();
        businessDateService.validateSettlementMutationAllowed();
        if (systemLockService.isSystemLocked()) {
            throw new RuntimeException("System is locked. Cashier reconciliation cannot be reopened.");
        }
        String normalizedReason = normalizeRequiredReason(reason);
        User actor = authenticatedUserService.getRequiredUser();
        CashierReconciliation entity = repository.findById(reconciliationId)
                .orElseThrow(() -> new com.casino.casinoerp.exception.ResourceNotFoundException("Cashier reconciliation not found."));
        if (!"SUBMITTED".equals(entity.getLifecycleStatus())) {
            throw new ResourceConflictException("Only a submitted cashier reconciliation can be reopened.");
        }
        entity.setLifecycleStatus("REOPENED");
        entity.setReopenedAt(LocalDateTime.now());
        entity.setReopenedBy(actor.getId());
        entity.setReopenReason(normalizedReason);
        CashierReconciliation saved = repository.save(entity);
        auditLogService.log("RECONCILIATION_REOPENED", "CASHIER_RECONCILIATION",
                saved.getId(), actor.getId(), auditDetail(saved, normalizedReason));
        User cashier = cashierUser(saved.getCashierUserId());
        return response(cashier, saved.getBusinessDate(), saved,
                tenderSnapshot(saved.getCashierUserId(), saved.getBusinessDate()));
    }

    @Transactional(readOnly = true)
    public void validatePostingAllowed(UUID cashierUserId, LocalDate businessDate) {
        repository.findByCashierUserIdAndBusinessDate(cashierUserId, businessDate)
                .filter(value -> "SUBMITTED".equals(value.getLifecycleStatus()))
                .ifPresent(value -> { throw new ResourceConflictException(
                        "Cashier reconciliation has already been submitted for this Business Date."); });
    }

    private CashierReconciliationResponse response(
            User actor, LocalDate businessDate, CashierReconciliation entity, TenderSnapshot tenders) {
        if (entity == null) {
            BigDecimal opening = openingBalanceRepository.findByCashierUserIdAndBusinessDate(actor.getId(), businessDate)
                    .map(CashierOpeningBalance::getOpeningCashAmount).orElse(null);
            BigDecimal expected = opening == null ? null
                    : opening.add(tenders.cashReceived()).subtract(tenders.cashPaid());
            return new CashierReconciliationResponse(null, businessDate, actor.getUsername(), actor.getFullName(),
                    "NOT_SUBMITTED", "OPEN", opening, tenders.cashReceived(), tenders.cashPaid(), expected, null, null,
                    tenders.buyIns(), tenders.cashOuts(), tenders.losingReturns(), Map.of(), null, null, null, null);
        }
        return new CashierReconciliationResponse(entity.getId(), businessDate, actor.getUsername(), actor.getFullName(),
                entity.getStatus(), entity.getLifecycleStatus(), entity.getOpeningCash(), tenders.cashReceived(), tenders.cashPaid(),
                entity.getExpectedClosingCash(), entity.getActualClosingCash(), entity.getVariance(),
                tenders.buyIns(), tenders.cashOuts(), tenders.losingReturns(), Map.copyOf(entity.getDenominations()),
                entity.getSubmittedAt(), entity.getRemarks(), entity.getReopenedAt(), entity.getReopenReason());
    }

    private CashierReconciliationResponse calculatedResponse(
            User actor, LocalDate businessDate, UUID id, String ignoredStatus, BigDecimal opening,
            BigDecimal actual, Map<Integer, Integer> denominations, LocalDateTime submittedAt,
            String remarks, TenderSnapshot tenders) {
        validateAmount(opening);
        BigDecimal expected = opening.add(tenders.cashReceived()).subtract(tenders.cashPaid());
        validateAmount(expected.abs());
        BigDecimal variance = actual.subtract(expected);
        String status = variance.signum() == 0 ? "BALANCED" : variance.signum() > 0 ? "OVER" : "SHORT";
        return new CashierReconciliationResponse(id, businessDate, actor.getUsername(), actor.getFullName(),
                status, "OPEN", opening, tenders.cashReceived(), tenders.cashPaid(), expected, actual, variance,
                tenders.buyIns(), tenders.cashOuts(), tenders.losingReturns(), denominations, submittedAt, normalizeRemarks(remarks), null, null);
    }

    private TenderSnapshot tenderSnapshot(UUID actorId, LocalDate businessDate) {
        List<ChipBuyIn> buyIns = buyInRepository.findByBusinessDateAndCreatedBy(businessDate, actorId);
        List<ChipCashOut> cashOuts = cashOutRepository.findByBusinessDateAndCreatedBy(businessDate, actorId);
        Map<String, TenderSummaryResponse> buyInTenders = summarize(
                buyIns, ChipBuyIn::getPaymentMode, ChipBuyIn::getAmountReceived);
        Map<String, TenderSummaryResponse> cashOutTenders = summarize(
                cashOuts, ChipCashOut::getPaymentMode, ChipCashOut::getCashPaid);
        List<LosingReturn> losingReturns = losingReturnRepository.findByBusinessDateAndCreatedBy(businessDate, actorId);
        Map<String, TenderSummaryResponse> losingReturnTenders = summarize(
                losingReturns, LosingReturn::getPaymentMode, LosingReturn::getAmountPaid);
        return new TenderSnapshot(buyInTenders, cashOutTenders,
                losingReturnTenders, buyInTenders.get("CASH").amount(),
                cashOutTenders.get("CASH").amount().add(losingReturnTenders.get("CASH").amount()));
    }

    private <T> Map<String, TenderSummaryResponse> summarize(
            List<T> values, Function<T, String> mode, Function<T, BigDecimal> amount) {
        Map<String, TenderSummaryResponse> result = new LinkedHashMap<>();
        for (PaymentMode paymentMode : PaymentMode.values()) {
            List<T> matching = values.stream().filter(value -> paymentMode.name().equals(mode.apply(value))).toList();
            BigDecimal total = matching.stream().map(amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            result.put(paymentMode.name(), new TenderSummaryResponse(matching.size(), total));
        }
        return Map.copyOf(result);
    }

    private Map<Integer, Integer> normalizeDenominations(Map<Integer, Integer> values) {
        Map<Integer, Integer> normalized = new LinkedHashMap<>();
        values.forEach((denomination, quantity) -> {
            if (!DENOMINATIONS.contains(denomination)) throw new IllegalArgumentException("Unsupported cash denomination: " + denomination);
            if (quantity == null || quantity < 0) throw new IllegalArgumentException("Cash denomination quantities must be non-negative integers.");
            if (quantity > 0) normalized.put(denomination, quantity);
        });
        return Map.copyOf(normalized);
    }

    private BigDecimal calculateActual(Map<Integer, Integer> values) {
        long total = 0;
        try {
            for (var entry : values.entrySet()) {
                total = Math.addExact(total, Math.multiplyExact(entry.getKey().longValue(), entry.getValue().longValue()));
            }
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Cash denomination total is too large.");
        }
        BigDecimal result = BigDecimal.valueOf(total);
        validateAmount(result);
        return result;
    }

    private void validateReplay(CashierReconciliation value, User actor, LocalDate date,
                                BigDecimal actual, Map<Integer, Integer> denominations) {
        if (!actor.getId().equals(value.getCashierUserId()) || !date.equals(value.getBusinessDate())
                || actual.compareTo(value.getActualClosingCash()) != 0
                || !denominations.equals(value.getDenominations())) {
            throw new ResourceConflictException("Idempotency key has already been used for a different reconciliation.");
        }
    }

    private BigDecimal requiredOpeningBalance(UUID cashierUserId, LocalDate businessDate) {
        return openingBalanceRepository.findByCashierUserIdAndBusinessDate(cashierUserId, businessDate)
                .map(CashierOpeningBalance::getOpeningCashAmount)
                .orElseThrow(() -> new ResourceConflictException(
                        "Opening Cash must be established for this cashier and Business Date before reconciliation."));
    }

    private void validateViewRole() {
        if (!currentUserRoleService.getCurrentRole().map(rolePermissionService::canViewCashierReconciliation).orElse(false))
            throw new RuntimeException("Access denied. Cashier reconciliation is restricted.");
    }
    private void validateSubmitRole() {
        if (!currentUserRoleService.getCurrentRole().map(rolePermissionService::canSubmitCashierReconciliation).orElse(false))
            throw new RuntimeException("Access denied. Only Cashier or Super Admin can submit reconciliation.");
    }
    private void validateReopenRole() {
        if (!currentUserRoleService.getCurrentRole().map(rolePermissionService::canReopenCashierReconciliation).orElse(false))
            throw new RuntimeException("Access denied. Only Director or Super Admin can reopen reconciliation.");
    }
    private LocalDate currentOpenBusinessDate() {
        return businessDateService.getCurrentOpenBusinessDate()
                .orElseThrow(() -> new IllegalStateException("Current business date is not opened."))
                .getBusinessDate();
    }
    private void validateAmount(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.compareTo(MAX_AMOUNT) > 0)
            throw new IllegalArgumentException("Cash amount must be zero or greater and within supported limits.");
    }
    private String normalizeRemarks(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String normalizeRequiredReason(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("A reopen reason is required.");
        String normalized = value.trim();
        if (normalized.length() > 1000) throw new IllegalArgumentException("Reopen reason must not exceed 1000 characters.");
        return normalized;
    }
    private String auditDetail(CashierReconciliation value, String reason) {
        return "cashier=" + value.getCashierUserId() + ", businessDate=" + value.getBusinessDate()
                + ", opening=" + value.getOpeningCash() + ", expected=" + value.getExpectedClosingCash()
                + ", actual=" + value.getActualClosingCash() + ", variance=" + value.getVariance()
                + ", result=" + value.getStatus() + (reason == null ? "" : ", reason=" + reason);
    }
    private User cashierUser(UUID userId) {
        return userRepository.findById(userId).orElseGet(() -> {
            User unavailable = new User(); unavailable.setId(userId);
            unavailable.setUsername("Unavailable"); unavailable.setFullName("Unavailable");
            return unavailable;
        });
    }
    private record TenderSnapshot(Map<String, TenderSummaryResponse> buyIns,
                                  Map<String, TenderSummaryResponse> cashOuts,
                                  Map<String, TenderSummaryResponse> losingReturns,
                                  BigDecimal cashReceived, BigDecimal cashPaid) {}
}
