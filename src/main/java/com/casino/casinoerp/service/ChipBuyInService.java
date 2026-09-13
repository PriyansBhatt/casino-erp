package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.ActorReferenceResponse;
import com.casino.casinoerp.dto.ChipBuyInResponse;
import com.casino.casinoerp.dto.CreateChipBuyInRequest;
import com.casino.casinoerp.dto.ChipBuyInHistoryResponse;
import com.casino.casinoerp.entity.Customer;
import com.casino.casinoerp.entity.CustomerSession;
import com.casino.casinoerp.entity.CustomerStatus;
import com.casino.casinoerp.entity.ChipBuyIn;
import com.casino.casinoerp.entity.PaymentMode;
import com.casino.casinoerp.entity.User;
import com.casino.casinoerp.entity.WalletTransaction;
import com.casino.casinoerp.repository.ChipBuyInRepository;
import com.casino.casinoerp.repository.CustomerRepository;
import com.casino.casinoerp.repository.CustomerSessionRepository;
import com.casino.casinoerp.repository.UserRepository;
import com.casino.casinoerp.exception.ResourceNotFoundException;
import com.casino.casinoerp.exception.ResourceConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ChipBuyInService {

    private final ChipBuyInRepository repository;
    private final CustomerRepository customerRepository;
    private final CustomerSessionRepository customerSessionRepository;
    private final SystemLockService systemLockService;
    private final BusinessDateService businessDateService;
    private final WalletTransactionService walletTransactionService;
    private final AuditLogService auditLogService;
    private final RolePermissionService rolePermissionService;
    private final CurrentUserRoleService currentUserRoleService;
    private final AuthenticatedUserService authenticatedUserService;
    private final UserRepository userRepository;
    private final CashierReconciliationService cashierReconciliationService;
    private final ChipCustodyService chipCustodyService;

    public ChipBuyInService(
            ChipBuyInRepository repository,
            CustomerRepository customerRepository,
            CustomerSessionRepository customerSessionRepository,
            SystemLockService systemLockService,
            WalletTransactionService walletTransactionService,
            BusinessDateService businessDateService,
            AuditLogService auditLogService,
            RolePermissionService rolePermissionService,
            CurrentUserRoleService currentUserRoleService,
            AuthenticatedUserService authenticatedUserService,
            UserRepository userRepository,
            CashierReconciliationService cashierReconciliationService,
            ChipCustodyService chipCustodyService) {

        this.repository = repository;
        this.customerRepository = customerRepository;
        this.customerSessionRepository = customerSessionRepository;
        this.systemLockService = systemLockService;
        this.walletTransactionService = walletTransactionService;
        this.businessDateService = businessDateService;
        this.auditLogService = auditLogService;
        this.rolePermissionService = rolePermissionService;
        this.currentUserRoleService = currentUserRoleService;
        this.authenticatedUserService = authenticatedUserService;
        this.userRepository = userRepository;
        this.cashierReconciliationService = cashierReconciliationService;
        this.chipCustodyService = chipCustodyService;
    }

    @Transactional
    public ChipBuyInResponse create(CreateChipBuyInRequest request) {
        String role = currentUserRoleService.getCurrentUserRole();

        if (!rolePermissionService.canBuyIn(role)) {
            throw new RuntimeException(
                    "Access denied. Only Cashier or Super Admin can create buy-in."
            );
        }

        String idempotencyKey = request.idempotencyKey().trim();
        ChipBuyIn existing = repository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) {
            validateIdempotentReplay(existing, request);
            return toResponse(existing);
        }

        businessDateService.validateNewOperationalMutationAllowed();
        businessDateService.validateBusinessDateIsOpen();

        if (systemLockService.isSystemLocked()) {
            throw new RuntimeException(
                    "System is locked. Buy-In transactions are not allowed."
            );
        }

        if (request.amountReceived().compareTo(request.totalChipValueIssued()) != 0) {
            throw new IllegalArgumentException("Amount received must equal total chip value issued.");
        }

        validatePaymentReference(request.paymentMode(), request.paymentReference());

        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found."));
        if (customer.getStatus() != CustomerStatus.ACTIVE) {
            throw new IllegalArgumentException("Customer must be ACTIVE to create a buy-in.");
        }

        CustomerSession session = customerSessionRepository.findByIdForUpdate(request.customerSessionId())
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

        User actor = authenticatedUserService.getRequiredUser();
        cashierReconciliationService.validatePostingAllowed(actor.getId(), businessDate);
        LocalDateTime createdAt = LocalDateTime.now();
        UUID buyInId = UUID.randomUUID();

        ChipBuyIn buyIn = new ChipBuyIn();
        buyIn.setId(buyInId);
        buyIn.setBuyInCode("BI-" + businessDate.toString().replace("-", "") + "-" + buyInId);
        buyIn.setCustomerId(request.customerId());
        buyIn.setCustomerSessionId(request.customerSessionId());
        buyIn.setAmountReceived(request.amountReceived());
        buyIn.setPaymentMode(request.paymentMode().name());
        buyIn.setPaymentReference(normalizeOptional(request.paymentReference()));
        buyIn.setTotalChipValueIssued(request.totalChipValueIssued());
        buyIn.setBusinessDate(businessDate);
        buyIn.setCreatedAt(createdAt);
        buyIn.setCreatedBy(actor.getId());
        buyIn.setRemarks(normalizeOptional(request.remarks()));
        buyIn.setIdempotencyKey(idempotencyKey);

        var custodyMovement = chipCustodyService.recordBuyIn(buyInId, session.getId(), businessDate,
                request.denominations(), request.totalChipValueIssued(), actor.getId());
        buyIn.setDenominations(new java.util.LinkedHashMap<>(custodyMovement.getDenominations()));

        WalletTransaction tx = new WalletTransaction();

        tx.setCustomerId(
                buyIn.getCustomerId()
        );

        tx.setCustomerSessionId(
                buyIn.getCustomerSessionId()
        );

        tx.setTransactionType("BUY_IN");

        tx.setAmount(
                buyIn.getTotalChipValueIssued()
        );

        tx.setRemarks(
                "Auto-created from Buy-In"
        );

        walletTransactionService.save(tx);

        ChipBuyIn saved = repository.save(buyIn);

        auditLogService.log(
                "CREATE_BUY_IN",
                "CHIP_BUY_IN",
                saved.getId(),
                currentUserRoleService.getCurrentUserId(),
                "Chip buy-in created: " + saved.getBuyInCode()
        );

        return toResponse(saved, actor);
    }

    private void validatePaymentReference(PaymentMode paymentMode, String paymentReference) {
        if (paymentMode != PaymentMode.CASH
                && (paymentReference == null || paymentReference.isBlank())) {
            throw new IllegalArgumentException("Payment reference is required for non-cash payment modes.");
        }
    }

    private void validateIdempotentReplay(ChipBuyIn existing, CreateChipBuyInRequest request) {
        if (!request.customerId().equals(existing.getCustomerId())
                || !request.customerSessionId().equals(existing.getCustomerSessionId())
                || request.amountReceived().compareTo(existing.getAmountReceived()) != 0
                || request.totalChipValueIssued().compareTo(existing.getTotalChipValueIssued()) != 0
                || !normalizeDenominations(request.denominations()).equals(existing.getDenominations())
                || !request.paymentMode().name().equals(existing.getPaymentMode())
                || !java.util.Objects.equals(
                        normalizeOptional(request.paymentReference()), existing.getPaymentReference())) {
            throw new ResourceConflictException(
                    "Idempotency key has already been used for a different buy-in request.");
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

    private ChipBuyInResponse toResponse(ChipBuyIn buyIn) {
        User actor = buyIn.getCreatedBy() == null
                ? null
                : userRepository.findById(buyIn.getCreatedBy()).orElse(null);
        return toResponse(buyIn, actor);
    }

    private ChipBuyInResponse toResponse(ChipBuyIn buyIn, User actor) {
        ActorReferenceResponse createdBy = actor == null
                ? null
                : new ActorReferenceResponse(actor.getId(), actor.getUsername());
        return new ChipBuyInResponse(
                buyIn.getId(),
                buyIn.getBuyInCode(),
                buyIn.getCustomerId(),
                buyIn.getCustomerSessionId(),
                buyIn.getAmountReceived(),
                buyIn.getPaymentMode(),
                buyIn.getTotalChipValueIssued(),
                java.util.Map.copyOf(buyIn.getDenominations()),
                buyIn.getPaymentReference(),
                buyIn.getBusinessDate(),
                buyIn.getCreatedAt(),
                createdBy,
                buyIn.getRemarks()
        );
    }

    public List<ChipBuyIn> getAllBuyIns() {
        return repository.findAll();
    }

    public List<ChipBuyIn> getBySessionId(UUID customerSessionId) {
        return repository.findByCustomerSessionId(customerSessionId);
    }

    @Transactional(readOnly = true)
    public List<ChipBuyInHistoryResponse> getCurrentBusinessDateHistory() {
        String role = currentUserRoleService.getCurrentUserRole();
        if (!rolePermissionService.canViewCashierTransactions(role)) {
            throw new RuntimeException("Access denied. Buy-In history is restricted.");
        }
        businessDateService.validateBusinessDateIsOpen();
        LocalDate date = businessDateService.getCurrentBusinessDate();
        List<ChipBuyIn> buyIns = repository.findByBusinessDateOrderByCreatedAtDesc(date);
        if (buyIns.isEmpty()) return List.of();
        var customerIds = buyIns.stream().map(ChipBuyIn::getCustomerId).distinct().toList();
        var actorIds = buyIns.stream().map(ChipBuyIn::getCreatedBy)
                .filter(java.util.Objects::nonNull).distinct().toList();
        var customers = customerRepository.findAllById(customerIds).stream()
                .collect(java.util.stream.Collectors.toMap(Customer::getId, java.util.function.Function.identity()));
        var actors = actorIds.isEmpty() ? java.util.Map.<UUID, User>of()
                : userRepository.findAllById(actorIds).stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, java.util.function.Function.identity()));
        return buyIns.stream().map(value -> {
            Customer customer = customers.get(value.getCustomerId());
            return new ChipBuyInHistoryResponse(toResponse(value, value.getCreatedBy() == null ? null : actors.get(value.getCreatedBy())),
                    customer == null ? null : customer.getCustomerCode(),
                    customer == null ? null : customer.getFullName());
        }).toList();
    }
}
