package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.ActorReferenceResponse;
import com.casino.casinoerp.dto.ChipCashOutResponse;
import com.casino.casinoerp.dto.CreateChipCashOutRequest;
import com.casino.casinoerp.dto.SessionFinancialPositionResponse;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.exception.ResourceConflictException;
import com.casino.casinoerp.exception.ResourceNotFoundException;
import com.casino.casinoerp.repository.ChipCashOutRepository;
import com.casino.casinoerp.repository.CustomerRepository;
import com.casino.casinoerp.repository.CustomerSessionRepository;
import com.casino.casinoerp.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class ChipCashOutService {

    private final ChipCashOutRepository repository;
    private final CustomerRepository customerRepository;
    private final CustomerSessionRepository sessionRepository;
    private final SystemLockService systemLockService;
    private final BusinessDateService businessDateService;
    private final SessionFinancialPositionService financialPositionService;
    private final WalletTransactionService walletTransactionService;
    private final AuditLogService auditLogService;
    private final RolePermissionService rolePermissionService;
    private final CurrentUserRoleService currentUserRoleService;
    private final AuthenticatedUserService authenticatedUserService;
    private final UserRepository userRepository;
    private final CashierReconciliationService cashierReconciliationService;
    private final ChipCustodyService chipCustodyService;

    public ChipCashOutService(
            ChipCashOutRepository repository,
            CustomerRepository customerRepository,
            CustomerSessionRepository sessionRepository,
            SystemLockService systemLockService,
            BusinessDateService businessDateService,
            SessionFinancialPositionService financialPositionService,
            WalletTransactionService walletTransactionService,
            AuditLogService auditLogService,
            RolePermissionService rolePermissionService,
            CurrentUserRoleService currentUserRoleService,
            AuthenticatedUserService authenticatedUserService,
            UserRepository userRepository,
            CashierReconciliationService cashierReconciliationService,
            ChipCustodyService chipCustodyService) {
        this.repository = repository;
        this.customerRepository = customerRepository;
        this.sessionRepository = sessionRepository;
        this.systemLockService = systemLockService;
        this.businessDateService = businessDateService;
        this.financialPositionService = financialPositionService;
        this.walletTransactionService = walletTransactionService;
        this.auditLogService = auditLogService;
        this.rolePermissionService = rolePermissionService;
        this.currentUserRoleService = currentUserRoleService;
        this.authenticatedUserService = authenticatedUserService;
        this.userRepository = userRepository;
        this.cashierReconciliationService = cashierReconciliationService;
        this.chipCustodyService = chipCustodyService;
    }

    @Transactional
    public ChipCashOutResponse create(CreateChipCashOutRequest request) {
        if (!rolePermissionService.canCashOut(currentUserRoleService.getCurrentUserRole())) {
            throw new RuntimeException("Access denied. Only Cashier or Super Admin can perform cash-out.");
        }

        String idempotencyKey = request.idempotencyKey().trim();
        ChipCashOut existing = repository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) {
            validateIdempotentReplay(existing, request);
            return toResponse(existing);
        }

        businessDateService.validateSettlementMutationAllowed();
        validatePositiveAmounts(request);
        if (request.cashPaid().compareTo(request.totalChipValueReturned()) != 0) {
            throw new IllegalArgumentException("Cash paid must equal total chip value returned.");
        }
        validatePaymentReference(request.paymentMode(), request.paymentReference());

        businessDateService.validateBusinessDateIsOpen();
        if (systemLockService.isSystemLocked()) {
            throw new RuntimeException("System is locked. Cash-Out transactions are not allowed.");
        }

        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found."));
        if (customer.getStatus() != CustomerStatus.ACTIVE) {
            throw new IllegalArgumentException("Customer must be ACTIVE to create a cash-out.");
        }

        CustomerSession session = sessionRepository.findByIdForUpdate(request.customerSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer session not found."));
        existing = repository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) {
            validateIdempotentReplay(existing, request);
            return toResponse(existing);
        }
        if (!"OPEN".equalsIgnoreCase(session.getStatus()) || session.getExitTime() != null) {
            throw new IllegalArgumentException("Customer session must be OPEN and unexited.");
        }
        if (!request.customerId().equals(session.getCustomerId())) {
            throw new IllegalArgumentException("Customer session does not belong to the supplied customer.");
        }

        LocalDate businessDate = businessDateService.getCurrentBusinessDate();
        if (!businessDate.equals(session.getBusinessDate())) {
            throw new IllegalArgumentException("Customer session does not belong to the current OPEN Business Date.");
        }

        SessionFinancialPositionResponse position = financialPositionService.getPosition(session.getId());
        if (!request.customerId().equals(position.customerId())
                || !businessDate.equals(position.businessDate())) {
            throw new IllegalArgumentException("Session financial position does not match the customer and Business Date.");
        }
        BigDecimal maximumCashOut = position.calculatedChipPosition().max(BigDecimal.ZERO);
        if (request.cashPaid().compareTo(maximumCashOut) > 0
                || request.totalChipValueReturned().compareTo(maximumCashOut) > 0) {
            throw new IllegalArgumentException("Cash-Out exceeds the authoritative session chip position.");
        }

        User actor = authenticatedUserService.getRequiredUser();
        cashierReconciliationService.validatePostingAllowed(actor.getId(), businessDate);
        UUID codeToken = UUID.randomUUID();
        ChipCashOut cashOut = new ChipCashOut();
        cashOut.setCashOutCode("CO-" + businessDate.toString().replace("-", "") + "-" + codeToken);
        cashOut.setCustomerId(request.customerId());
        cashOut.setCustomerSessionId(request.customerSessionId());
        cashOut.setCashPaid(request.cashPaid());
        cashOut.setTotalChipValueReturned(request.totalChipValueReturned());
        cashOut.setPaymentMode(request.paymentMode().name());
        cashOut.setPaymentReference(normalizeOptional(request.paymentReference()));
        cashOut.setSameCustomerVerified(true);
        cashOut.setThirdPartyAttempt(false);
        cashOut.setCreatedBy(actor.getId());
        cashOut.setBusinessDate(businessDate);
        cashOut.setCreatedAt(LocalDateTime.now());
        cashOut.setRemarks(normalizeOptional(request.remarks()));
        cashOut.setIdempotencyKey(idempotencyKey);

        ChipCashOut saved = repository.save(cashOut);

        ChipCustodyMovement custodyMovement = chipCustodyService.recordCashOut(saved.getId(), session.getId(),
                businessDate, request.denominations(), request.totalChipValueReturned(), actor.getId());
        saved.setDenominations(new java.util.LinkedHashMap<>(custodyMovement.getDenominations()));

        WalletTransaction transaction = new WalletTransaction();
        transaction.setCustomerId(saved.getCustomerId());
        transaction.setCustomerSessionId(saved.getCustomerSessionId());
        transaction.setTransactionType("CASH_OUT");
        transaction.setAmount(saved.getCashPaid());
        transaction.setRemarks("Auto-created from Cash-Out");
        walletTransactionService.save(transaction);

        auditLogService.log(
                "CREATE_CASH_OUT", "CHIP_CASH_OUT", saved.getId(), actor.getId(),
                "Chip cash-out created: " + saved.getCashOutCode());

        return toResponse(saved, actor);
    }

    private void validatePositiveAmounts(CreateChipCashOutRequest request) {
        if (request.cashPaid() == null || request.cashPaid().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Cash paid must be greater than 0.");
        }
        if (request.totalChipValueReturned() == null
                || request.totalChipValueReturned().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Total chip value returned must be greater than 0.");
        }
    }

    private void validatePaymentReference(PaymentMode paymentMode, String paymentReference) {
        if (paymentMode != PaymentMode.CASH
                && (paymentReference == null || paymentReference.isBlank())) {
            throw new IllegalArgumentException("Payment reference is required for non-cash payment modes.");
        }
    }

    private void validateIdempotentReplay(ChipCashOut existing, CreateChipCashOutRequest request) {
        if (!request.customerId().equals(existing.getCustomerId())
                || !request.customerSessionId().equals(existing.getCustomerSessionId())
                || request.cashPaid().compareTo(existing.getCashPaid()) != 0
                || request.totalChipValueReturned().compareTo(existing.getTotalChipValueReturned()) != 0
                || !normalizeDenominations(request.denominations()).equals(existing.getDenominations())
                || !request.paymentMode().name().equals(existing.getPaymentMode())
                || !Objects.equals(normalizeOptional(request.paymentReference()), existing.getPaymentReference())) {
            throw new ResourceConflictException(
                    "Idempotency key has already been used for a different cash-out request.");
        }
    }

    private java.util.Map<Integer, Long> normalizeDenominations(java.util.Map<Integer, Long> values) {
        if (values == null) return java.util.Map.of();
        java.util.Map<Integer, Long> normalized = new java.util.LinkedHashMap<>();
        values.forEach((denomination, quantity) -> {
            if (quantity != null && quantity > 0) normalized.put(denomination, quantity);
        });
        return normalized;
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ChipCashOutResponse toResponse(ChipCashOut cashOut) {
        User actor = cashOut.getCreatedBy() == null
                ? null
                : userRepository.findById(cashOut.getCreatedBy()).orElse(null);
        return toResponse(cashOut, actor);
    }

    private ChipCashOutResponse toResponse(ChipCashOut cashOut, User actor) {
        ActorReferenceResponse createdBy = actor == null
                ? null
                : new ActorReferenceResponse(actor.getId(), actor.getUsername());
        return new ChipCashOutResponse(
                cashOut.getId(), cashOut.getCashOutCode(), cashOut.getCustomerId(),
                cashOut.getCustomerSessionId(), cashOut.getCashPaid(),
                cashOut.getTotalChipValueReturned(), java.util.Map.copyOf(cashOut.getDenominations()), cashOut.getPaymentMode(),
                cashOut.getPaymentReference(), cashOut.getBusinessDate(), cashOut.getCreatedAt(),
                createdBy, cashOut.getRemarks());
    }

    @Transactional(readOnly = true)
    public List<ChipCashOutResponse> getBySessionId(UUID customerSessionId) {
        return history(repository.findHistoryBySession(customerSessionId));
    }

    @Transactional(readOnly = true)
    public List<ChipCashOutResponse> getAllCashOuts() {
        return history(repository.findHistory());
    }

    private List<ChipCashOutResponse> history(List<ChipCashOut> rows) {
        var ids = rows.stream().map(ChipCashOut::getCreatedBy).filter(Objects::nonNull).distinct().toList();
        var actors = new java.util.HashMap<UUID, User>();
        if (!ids.isEmpty()) userRepository.findAllById(ids).forEach(actor -> actors.put(actor.getId(), actor));
        return rows.stream().map(row -> toResponse(row, actors.get(row.getCreatedBy()))).toList();
    }
}
