package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.ActorReferenceResponse;
import com.casino.casinoerp.dto.CreateVerifiedGamingResultRequest;
import com.casino.casinoerp.dto.VerifiedGamingResultResponse;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.exception.ResourceNotFoundException;
import com.casino.casinoerp.repository.CustomerRepository;
import com.casino.casinoerp.repository.CustomerSessionRepository;
import com.casino.casinoerp.repository.VerifiedGamingResultRepository;
import com.casino.casinoerp.repository.PitTableRepository;
import com.casino.casinoerp.repository.PitTableCustomerAssignmentRepository;
import com.casino.casinoerp.repository.UserRepository;
import com.casino.casinoerp.exception.ResourceConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.math.BigDecimal;
import java.util.UUID;

@Service
public class VerifiedGamingResultService {
    private static final List<Integer> SUPPORTED_DENOMINATIONS = List.of(500, 1000, 5000, 10000, 25000);
    private static final Set<Integer> SUPPORTED_DENOMINATION_SET = Set.copyOf(SUPPORTED_DENOMINATIONS);
    private static final BigDecimal MAX_DATABASE_AMOUNT = new BigDecimal("99999999999999999.99");

    private final VerifiedGamingResultRepository repository;
    private final CustomerRepository customerRepository;
    private final CustomerSessionRepository sessionRepository;
    private final PitTableRepository tableRepository;
    private final PitTableCustomerAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final BusinessDateService businessDateService;
    private final SystemLockService systemLockService;
    private final CurrentUserRoleService currentUserRoleService;
    private final RolePermissionService rolePermissionService;
    private final AuthenticatedUserService authenticatedUserService;
    private final AuditLogService auditLogService;
    private final PitTableAccessService tableAccess;

    public VerifiedGamingResultService(
            VerifiedGamingResultRepository repository,
            CustomerRepository customerRepository,
            CustomerSessionRepository sessionRepository,
            PitTableRepository tableRepository,
            PitTableCustomerAssignmentRepository assignmentRepository,
            UserRepository userRepository,
            BusinessDateService businessDateService,
            SystemLockService systemLockService,
            CurrentUserRoleService currentUserRoleService,
            RolePermissionService rolePermissionService,
            AuthenticatedUserService authenticatedUserService,
            AuditLogService auditLogService,
            PitTableAccessService tableAccess) {
        this.repository = repository;
        this.customerRepository = customerRepository;
        this.sessionRepository = sessionRepository;
        this.tableRepository = tableRepository;
        this.assignmentRepository = assignmentRepository;
        this.userRepository = userRepository;
        this.businessDateService = businessDateService;
        this.systemLockService = systemLockService;
        this.currentUserRoleService = currentUserRoleService;
        this.rolePermissionService = rolePermissionService;
        this.authenticatedUserService = authenticatedUserService;
        this.auditLogService = auditLogService;
        this.tableAccess = tableAccess;
    }

    @Transactional
    public VerifiedGamingResultResponse create(CreateVerifiedGamingResultRequest request) {
        if (!currentUserRoleService.getCurrentRole()
                .map(rolePermissionService::canRecordVerifiedGamingResult)
                .orElse(false)) {
            throw new RuntimeException("Access denied. Only Dealer, Pit Supervisor or Super Admin can record verified gaming results.");
        }

        String idempotencyKey = request.idempotencyKey().trim();
        Map<Integer, Integer> denominations = validateAndNormalizeDenominations(request.denominations());
        BigDecimal calculatedAmount = calculateAmount(denominations);
        if (request.amount() != null && request.amount().compareTo(calculatedAmount) != 0) {
            throw new IllegalArgumentException("Supplied amount does not match the denomination total.");
        }
        VerifiedGamingResult existing = repository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) {
            validateIdempotentReplay(existing, request, denominations, calculatedAmount);
            return toResponse(existing, null);
        }

        tableAccess.requireOperationalAccess(request.pitTableId());
        businessDateService.validateNewOperationalMutationAllowed();
        businessDateService.validateBusinessDateIsOpen();
        if (systemLockService.isSystemLocked()) {
            throw new RuntimeException("System is locked. Verified gaming results are not allowed.");
        }

        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found."));
        if (customer.getStatus() != CustomerStatus.ACTIVE) {
            throw new IllegalArgumentException("Customer must be ACTIVE to record a verified gaming result.");
        }

        CustomerSession session = sessionRepository.findByIdForUpdate(request.customerSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer session not found."));
        existing = repository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) {
            validateIdempotentReplay(existing, request, denominations, calculatedAmount);
            return toResponse(existing, null);
        }
        if (!request.customerId().equals(session.getCustomerId())) {
            throw new IllegalArgumentException("Customer session does not belong to the supplied customer.");
        }
        if ((!"OPEN".equalsIgnoreCase(session.getStatus()) || session.getExitTime() != null)) {
            throw new IllegalArgumentException("Customer session must be OPEN and unexited.");
        }

        LocalDate businessDate = businessDateService.getCurrentBusinessDate();
        if (!businessDate.equals(session.getBusinessDate())) {
            throw new IllegalArgumentException("Customer session does not belong to the current OPEN Business Date.");
        }

        PitTable table = tableRepository.findByIdForUpdate(request.pitTableId())
                .orElseThrow(() -> new ResourceNotFoundException("Pit table not found."));
        tableAccess.requireOperationalAccess(table.getId());
        if (!"OPEN".equalsIgnoreCase(table.getStatus())) {
            throw new IllegalArgumentException("Pit table must be OPEN.");
        }
        if (!businessDate.equals(table.getBusinessDate())) {
            throw new IllegalArgumentException("Pit table does not belong to the current OPEN Business Date.");
        }

        PitTableCustomerAssignment assignment = assignmentRepository.findById(request.assignmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Pit table customer assignment not found."));
        if (assignment.getStatus() != PitTableCustomerAssignmentStatus.ACTIVE
                || !request.pitTableId().equals(assignment.getPitTableId())
                || !request.customerId().equals(assignment.getCustomerId())
                || !request.customerSessionId().equals(assignment.getCustomerSessionId())
                || !businessDate.equals(assignment.getBusinessDate())) {
            throw new IllegalArgumentException("Customer does not have a matching ACTIVE assignment to this Pit Table.");
        }

        User actor = authenticatedUserService.getRequiredUser();
        VerifiedGamingResult result = new VerifiedGamingResult();
        result.setCustomerId(request.customerId());
        result.setCustomerSessionId(request.customerSessionId());
        result.setPitTableId(request.pitTableId());
        result.setAssignmentId(request.assignmentId());
        result.setBusinessDate(businessDate);
        result.setSourceType(request.sourceType());
        result.setResultType(request.resultType());
        result.setDenominations(denominations);
        result.setAmount(calculatedAmount);
        result.setIdempotencyKey(idempotencyKey);
        result.setCreatedAt(LocalDateTime.now());
        result.setCreatedBy(actor.getId());

        VerifiedGamingResult saved = repository.save(result);
        auditLogService.log(
                "CREATE_VERIFIED_GAMING_RESULT",
                "VERIFIED_GAMING_RESULT",
                saved.getId(),
                actor.getId(),
                "Verified " + saved.getSourceType() + " " + saved.getResultType() + " recorded"
        );
        return toResponse(saved, actor);
    }

    private void validateIdempotentReplay(
            VerifiedGamingResult existing, CreateVerifiedGamingResultRequest request,
            Map<Integer, Integer> denominations, BigDecimal calculatedAmount) {
        if (!java.util.Objects.equals(existing.getCreatedBy(), authenticatedUserService.getRequiredUser().getId())
                || !request.customerId().equals(existing.getCustomerId())
                || !request.customerSessionId().equals(existing.getCustomerSessionId())
                || !request.pitTableId().equals(existing.getPitTableId())
                || !request.assignmentId().equals(existing.getAssignmentId())
                || request.sourceType() != existing.getSourceType()
                || request.resultType() != existing.getResultType()
                || calculatedAmount.compareTo(existing.getAmount()) != 0
                || !denominations.equals(existing.getDenominations())) {
            throw new ResourceConflictException(
                    "Idempotency key has already been used for a different verified gaming result.");
        }
    }

    public List<VerifiedGamingResultResponse> getBySession(UUID sessionId) {
        tableAccess.requireCustomerSessionAccess(sessionId);
        var stream = repository.findByCustomerSessionIdOrderByCreatedAtAsc(sessionId).stream();
        if (tableAccess.isDealer()) {
            UUID operationId = tableAccess.currentDealerOperationId()
                    .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException(
                            "Active Dealer assignment to a Pit Table operation is required."));
            stream = stream.filter(result -> operationId.equals(result.getPitTableId()));
        }
        return stream
                .map(result -> toResponse(result, null))
                .toList();
    }

    private VerifiedGamingResultResponse toResponse(VerifiedGamingResult result, User actor) {
        User resolvedActor = actor == null
                ? userRepository.findById(result.getCreatedBy()).orElse(null)
                : actor;
        ActorReferenceResponse createdBy = new ActorReferenceResponse(result.getCreatedBy(),
                resolvedActor == null ? null : resolvedActor.getUsername());
        return new VerifiedGamingResultResponse(
                result.getId(), result.getCustomerId(), result.getCustomerSessionId(),
                result.getPitTableId(), result.getAssignmentId(),
                result.getBusinessDate(), result.getSourceType(), result.getResultType(),
                Map.copyOf(result.getDenominations()), result.getAmount(), result.getCreatedAt(), createdBy
        );
    }

    private Map<Integer, Integer> validateAndNormalizeDenominations(Map<Integer, Integer> requested) {
        if (requested == null || requested.isEmpty()) {
            throw new IllegalArgumentException("At least one denomination quantity must be greater than zero.");
        }
        for (Map.Entry<Integer, Integer> entry : requested.entrySet()) {
            if (entry.getKey() == null || !SUPPORTED_DENOMINATION_SET.contains(entry.getKey())) {
                throw new IllegalArgumentException("Unsupported chip denomination: " + entry.getKey());
            }
            if (entry.getValue() == null || entry.getValue() < 0) {
                throw new IllegalArgumentException("Denomination quantities must be non-negative integers.");
            }
        }
        Map<Integer, Integer> normalized = new LinkedHashMap<>();
        SUPPORTED_DENOMINATIONS.forEach(denomination -> {
            int quantity = requested.getOrDefault(denomination, 0);
            if (quantity > 0) normalized.put(denomination, quantity);
        });
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("At least one denomination quantity must be greater than zero.");
        }
        return normalized;
    }

    private BigDecimal calculateAmount(Map<Integer, Integer> denominations) {
        long total = 0;
        try {
            for (Map.Entry<Integer, Integer> entry : denominations.entrySet()) {
                total = Math.addExact(total, Math.multiplyExact(entry.getKey().longValue(), entry.getValue().longValue()));
            }
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Denomination total exceeds the supported monetary range.");
        }
        BigDecimal amount = BigDecimal.valueOf(total);
        if (amount.compareTo(MAX_DATABASE_AMOUNT) > 0) {
            throw new IllegalArgumentException("Denomination total exceeds the supported monetary range.");
        }
        return amount;
    }
}
