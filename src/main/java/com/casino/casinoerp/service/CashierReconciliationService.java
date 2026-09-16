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

@Service
public class CashierReconciliationService {
    private static final Set<Integer> DENOMINATIONS = Set.of(5, 10, 20, 50, 100, 500, 1000);
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999999999999.99");
    private final CashierReconciliationRepository repository;
    private final CashierReconciliationReadRepository reads;
    private final BusinessDateService businessDateService;
    private final AuthenticatedUserService authenticatedUserService;
    private final CurrentUserRoleService currentUserRoleService;
    private final RolePermissionService rolePermissionService;
    private final SystemLockService systemLockService;
    private final AuditLogService auditLogService;
    private final UserRepository userRepository;
    private final CashierOpeningBalanceRepository openingBalanceRepository;

    public CashierReconciliationService(
            CashierReconciliationRepository repository,
            CashierReconciliationReadRepository reads,
            BusinessDateService businessDateService,
            AuthenticatedUserService authenticatedUserService,
            CurrentUserRoleService currentUserRoleService,
            RolePermissionService rolePermissionService,
            SystemLockService systemLockService,
            AuditLogService auditLogService,
            UserRepository userRepository,
            CashierOpeningBalanceRepository openingBalanceRepository) {
        this.repository = repository;
        this.reads = reads;
        this.businessDateService = businessDateService;
        this.authenticatedUserService = authenticatedUserService;
        this.currentUserRoleService = currentUserRoleService;
        this.rolePermissionService = rolePermissionService;
        this.systemLockService = systemLockService;
        this.auditLogService = auditLogService;
        this.userRepository = userRepository;
        this.openingBalanceRepository = openingBalanceRepository;
    }

    @Transactional(readOnly = true)
    public CashierReconciliationResponse getCurrent() {
        validateViewRole();
        User actor = authenticatedUserService.getRequiredUser();
        LocalDate businessDate = currentOpenBusinessDate();
        CashierReconciliation existing = repository
                .findByCashierUserIdAndBusinessDate(actor.getId(), businessDate).orElse(null);
        if (existing != null && "SUBMITTED".equals(existing.getLifecycleStatus())) return snapshot(actor, existing);
        return liveResponse(actor, businessDate, existing, tenderSnapshot(actor.getId(), businessDate));
    }

    @Transactional(readOnly = true)
    public List<CashierReconciliationResponse> getSubmittedForCurrentBusinessDate() {
        validateReopenRole();
        LocalDate date = currentOpenBusinessDate();
        var values = repository.findByBusinessDateOrderBySubmittedAtDescIdDesc(date);
        var ids = values.stream().map(CashierReconciliation::getCashierUserId).distinct().toList();
        var actors = new HashMap<UUID, User>();
        if (!ids.isEmpty()) userRepository.findAllById(ids).forEach(user -> actors.put(user.getId(), user));
        return values.stream().map(value -> snapshot(
                actors.getOrDefault(value.getCashierUserId(), unavailableUser(value.getCashierUserId())), value)).toList();
    }

    @Transactional(readOnly = true)
    public CashierReconciliationResponse preview(CashierReconciliationRequest request) {
        validateSubmitRole();
        User actor = authenticatedUserService.getRequiredUser();
        LocalDate businessDate = currentOpenBusinessDate();
        validateExpectedDate(request.expectedBusinessDate(), businessDate);
        Map<Integer, Integer> denominations = normalizeDenominations(request.denominations());
        BigDecimal actual = calculateActual(denominations);
        TenderSnapshot tenders = tenderSnapshot(actor.getId(), businessDate);
        BigDecimal opening = requiredOpeningBalance(actor.getId(), businessDate);
        var existing = repository.findByCashierUserIdAndBusinessDate(actor.getId(), businessDate).orElse(null);
        return calculatedResponse(actor, businessDate, existing, opening, actual,
                denominations, request.remarks(), tenders);
    }

    @Transactional
    public CashierReconciliationResponse submit(CashierReconciliationRequest request) {
        validateSubmitRole();
        User actor = authenticatedUserService.getRequiredUser();
        Map<Integer, Integer> denominations = normalizeDenominations(request.denominations());
        BigDecimal actual = calculateActual(denominations);
        if (request.idempotencyKey() == null || request.idempotencyKey().isBlank())
            throw new IllegalArgumentException("Idempotency key is required.");
        String key = request.idempotencyKey().trim();
        // Serialize replay with reopen/resubmit as well as new posting, without requiring a new-operation gate.
        businessDateService.lockLifecycleForReconciliation();
        CashierReconciliation replay = repository.findByIdempotencyKey(key).orElse(null);
        if (replay != null) {
            validateReplay(replay, actor, request.expectedBusinessDate(), actual, denominations);
            if (!Objects.equals(replay.getRemarks(), normalizeRemarks(request.remarks())))
                throw new ResourceConflictException("Idempotency key has already been used with different remarks.");
            if (!"SUBMITTED".equals(replay.getLifecycleStatus()))
                throw new ResourceConflictException("This reconciliation submission has been superseded. Refresh the reconciliation before continuing.");
            return snapshot(actor, replay);
        }
        businessDateService.validateSettlementMutationAllowed();
        if (systemLockService.isSystemLocked()) {
            throw new RuntimeException("System is locked. Cashier reconciliation cannot be submitted.");
        }
        LocalDate businessDate = currentOpenBusinessDate();
        validateExpectedDate(request.expectedBusinessDate(), businessDate);
        CashierReconciliation existing = repository.findByCashierUserIdAndBusinessDate(actor.getId(), businessDate).orElse(null);
        if (existing != null && !"REOPENED".equals(existing.getLifecycleStatus())) {
            throw new ResourceConflictException("This reconciliation submission has been superseded; reconciliation has already been submitted for this Business Date. Refresh before continuing.");
        }

        // Only the exact reopened lifecycle observed when preparing this operation may be replaced.
        // Historical receipts are not retained: superseded operations are rejected, never reconstructed.
        if ((existing == null && request.expectedReopenedAt() != null)
                || (existing != null && (request.expectedReopenedAt() == null
                || !Objects.equals(request.expectedReopenedAt(), existing.getReopenedAt())))) {
            throw new ResourceConflictException("This reconciliation submission has been superseded. Refresh the reconciliation before continuing.");
        }

        BigDecimal opening = requiredOpeningBalance(actor.getId(), businessDate);
        validateAmount(opening);
        TenderSnapshot tenders = tenderSnapshot(actor.getId(), businessDate);
        BigDecimal expected = opening.add(tenders.cashReceived()).subtract(tenders.cashPaid());
        validateAmount(expected.abs());
        BigDecimal variance = actual.subtract(expected);
        validateAmount(variance.abs());
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
        return snapshot(actor, saved);
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
        validateExpectedDate(entity.getBusinessDate(), currentOpenBusinessDate());
        if (!"SUBMITTED".equals(entity.getLifecycleStatus())) {
            throw new ResourceConflictException("Only a submitted cashier reconciliation can be reopened.");
        }
        entity.setLifecycleStatus("REOPENED");
        entity.setReopenedAt(LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        entity.setReopenedBy(actor.getId());
        entity.setReopenReason(normalizedReason);
        CashierReconciliation saved = repository.save(entity);
        auditLogService.log("RECONCILIATION_REOPENED", "CASHIER_RECONCILIATION",
                saved.getId(), actor.getId(), auditDetail(saved, normalizedReason));
        User cashier = cashierUser(saved.getCashierUserId());
        return snapshot(cashier, saved);
    }

    @Transactional(readOnly = true)
    public void validatePostingAllowed(UUID cashierUserId, LocalDate businessDate) {
        repository.findByCashierUserIdAndBusinessDate(cashierUserId, businessDate)
                .filter(value -> "SUBMITTED".equals(value.getLifecycleStatus()))
                .ifPresent(value -> { throw new ResourceConflictException(
                        "Cashier reconciliation has already been submitted for this Business Date."); });
    }

    /** Saved review deliberately omits tender totals: those were never stored in the submission. */
    private CashierReconciliationResponse snapshot(User actor, CashierReconciliation entity) {
        return new CashierReconciliationResponse(entity.getId(), entity.getBusinessDate(), actor.getUsername(), actor.getFullName(),
                entity.getStatus(), entity.getLifecycleStatus(), entity.getOpeningCash(), null, null,
                entity.getExpectedClosingCash(), entity.getActualClosingCash(), entity.getVariance(),
                null, null, null, Map.copyOf(entity.getDenominations()), entity.getSubmittedAt(), entity.getRemarks(),
                entity.getReopenedAt(), entity.getReopenReason(), "SUBMITTED".equals(entity.getLifecycleStatus())
                ? "SUBMITTED_SNAPSHOT" : "LAST_SUBMISSION_SNAPSHOT");
    }

    private CashierReconciliationResponse liveResponse(User actor, LocalDate date,
            CashierReconciliation existing, TenderSnapshot tenders) {
        BigDecimal opening = openingBalanceRepository.findByCashierUserIdAndBusinessDate(actor.getId(), date)
                .map(CashierOpeningBalance::getOpeningCashAmount).orElse(null);
        BigDecimal expected = opening == null ? null : opening.add(tenders.cashReceived()).subtract(tenders.cashPaid());
        if (expected != null) validateAmount(expected.abs());
        return new CashierReconciliationResponse(existing == null ? null : existing.getId(), date,
                actor.getUsername(), actor.getFullName(), existing == null ? "NOT_SUBMITTED" : null,
                existing == null ? "OPEN" : existing.getLifecycleStatus(), opening, tenders.cashReceived(), tenders.cashPaid(),
                expected, null, null, tenders.buyIns(), tenders.cashOuts(), tenders.losingReturns(), Map.of(),
                existing == null ? null : existing.getSubmittedAt(), null,
                existing == null ? null : existing.getReopenedAt(), existing == null ? null : existing.getReopenReason(), "LIVE");
    }

    private CashierReconciliationResponse calculatedResponse(User actor, LocalDate date,
            CashierReconciliation existing, BigDecimal opening, BigDecimal actual,
            Map<Integer, Integer> denominations, String remarks, TenderSnapshot tenders) {
        validateAmount(opening);
        BigDecimal expected = opening.add(tenders.cashReceived()).subtract(tenders.cashPaid());
        validateAmount(expected.abs());
        BigDecimal variance = actual.subtract(expected);
        validateAmount(variance.abs());
        String status = variance.signum() == 0 ? "BALANCED" : variance.signum() > 0 ? "OVER" : "SHORT";
        return new CashierReconciliationResponse(existing == null ? null : existing.getId(), date,
                actor.getUsername(), actor.getFullName(), status, existing == null ? "OPEN" : existing.getLifecycleStatus(),
                opening, tenders.cashReceived(), tenders.cashPaid(), expected, actual, variance,
                tenders.buyIns(), tenders.cashOuts(), tenders.losingReturns(), denominations,
                existing == null ? null : existing.getSubmittedAt(), normalizeRemarks(remarks),
                existing == null ? null : existing.getReopenedAt(), existing == null ? null : existing.getReopenReason(), "PREVIEW");
    }

    private TenderSnapshot tenderSnapshot(UUID actorId, LocalDate businessDate) {
        var rows = reads.tenders(actorId, businessDate);
        var buyIns = summarize(rows, "BUY_IN");
        var cashOuts = summarize(rows, "CASH_OUT");
        var losingReturns = summarize(rows, "LOSING_RETURN");
        return new TenderSnapshot(buyIns, cashOuts, losingReturns, buyIns.get("CASH").amount(),
                cashOuts.get("CASH").amount().add(losingReturns.get("CASH").amount()));
    }

    private Map<String, TenderSummaryResponse> summarize(List<CashierReconciliationReadRepository.Tender> rows, String source) {
        Map<String, TenderSummaryResponse> result = new LinkedHashMap<>();
        for (PaymentMode mode : PaymentMode.values()) result.put(mode.name(), new TenderSummaryResponse(0, BigDecimal.ZERO));
        for (var row : rows) {
            if (source.equals(row.source()) && result.containsKey(row.paymentMode()))
                result.put(row.paymentMode(), new TenderSummaryResponse(row.count(), row.amount()));
        }
        return Map.copyOf(result);
    }

    private Map<Integer, Integer> normalizeDenominations(Map<Integer, Integer> values) {
        if (values == null) throw new IllegalArgumentException("Cash denominations are required.");
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
        if (!actor.getId().equals(value.getCashierUserId()) || !Objects.equals(date, value.getBusinessDate())
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
    private void validateExpectedDate(LocalDate expected, LocalDate current) {
        if (!current.equals(expected)) throw new ResourceConflictException(
                "Business Date changed or was not supplied. Refresh and recount for the current operational date.");
    }
    private User unavailableUser(UUID id) {
        User user = new User(); user.setId(id); user.setUsername("Unavailable"); user.setFullName("Unavailable"); return user;
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
