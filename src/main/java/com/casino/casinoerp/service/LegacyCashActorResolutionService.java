package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.LegacyCashActorResolutionRequest;
import com.casino.casinoerp.dto.LegacyCashActorResolutionResponse;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.exception.ResourceConflictException;
import com.casino.casinoerp.exception.ResourceNotFoundException;
import com.casino.casinoerp.repository.*;
import com.casino.casinoerp.security.Role;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class LegacyCashActorResolutionService {
    static final String LEGACY_UNVERIFIED = "LEGACY_UNVERIFIED";
    static final String LEGACY_RESOLVED = "LEGACY_RESOLVED";
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999999999999.99");

    private final LegacyCashActorResolutionRepository resolutions;
    private final UserRepository users;
    private final CashierOpeningBalanceRepository openingBalances;
    private final CashierReconciliationRepository reconciliations;
    private final ChipBuyInRepository buyIns;
    private final ChipCashOutRepository cashOuts;
    private final LosingReturnRepository losingReturns;
    private final BusinessDateService businessDates;
    private final SystemLockService systemLock;
    private final AuthenticatedUserService authenticatedUser;
    private final CurrentUserRoleService currentUserRole;
    private final RolePermissionService permissions;
    private final AuditLogService audit;

    public LegacyCashActorResolutionService(
            LegacyCashActorResolutionRepository resolutions, UserRepository users,
            CashierOpeningBalanceRepository openingBalances,
            CashierReconciliationRepository reconciliations,
            ChipBuyInRepository buyIns, ChipCashOutRepository cashOuts,
            LosingReturnRepository losingReturns, BusinessDateService businessDates,
            SystemLockService systemLock, AuthenticatedUserService authenticatedUser,
            CurrentUserRoleService currentUserRole, RolePermissionService permissions,
            AuditLogService audit) {
        this.resolutions = resolutions;
        this.users = users;
        this.openingBalances = openingBalances;
        this.reconciliations = reconciliations;
        this.buyIns = buyIns;
        this.cashOuts = cashOuts;
        this.losingReturns = losingReturns;
        this.businessDates = businessDates;
        this.systemLock = systemLock;
        this.authenticatedUser = authenticatedUser;
        this.currentUserRole = currentUserRole;
        this.permissions = permissions;
        this.audit = audit;
    }

    @Transactional
    public LegacyCashActorResolutionResponse resolve(
            UUID targetUserId, LegacyCashActorResolutionRequest request) {
        validateSuperAdmin();
        validateRequest(request);
        String reason = request.reason().trim();
        String key = request.idempotencyKey().trim();

        var replay = resolutions.findByIdempotencyKey(key);
        if (replay.isPresent()) {
            validateExactReplay(replay.get(), targetUserId, reason);
            return response(replay.get(), requiredUser(targetUserId));
        }

        BusinessDate openDate = businessDates.getCurrentOpenBusinessDate()
                .orElseThrow(() -> new ResourceConflictException("Current business date is not opened."));
        if (systemLock.isSystemLocked()) {
            throw new ResourceConflictException(
                    "System is locked. Legacy cash-activity resolution is not allowed.");
        }

        User target = requiredUser(targetUserId);
        Role targetRole = Role.fromValue(target.getRole())
                .orElseThrow(() -> new ResourceConflictException(
                        "Target user has an unknown role and cannot be legacy resolved."));
        if (targetRole == Role.CASHIER) {
            throw new ResourceConflictException(
                    "CASHIER activity must use the normal Opening Cash and reconciliation lifecycle.");
        }

        LocalDate date = openDate.getBusinessDate();
        if (openingBalances.findByCashierUserIdAndBusinessDate(targetUserId, date).isPresent()) {
            throw new ResourceConflictException(
                    "Target actor already has an authoritative Opening Cash record.");
        }
        if (reconciliations.findByCashierUserIdAndBusinessDate(targetUserId, date).isPresent()) {
            throw new ResourceConflictException(
                    "Target actor already has a normal cashier reconciliation.");
        }
        if (resolutions.findByActorUserIdAndBusinessDate(targetUserId, date).isPresent()) {
            throw new ResourceConflictException(
                    "Target actor already has a legacy cash-activity resolution for this Business Date.");
        }

        CashTotals totals = calculateCashTotals(targetUserId, date);
        if (totals.transactionCount() == 0) {
            throw new ResourceConflictException(
                    "Target actor has no persisted CASH activity for the current Business Date.");
        }

        User resolver = authenticatedUser.getRequiredUser();
        LegacyCashActorResolution value = new LegacyCashActorResolution();
        value.setActorUserId(targetUserId);
        value.setBusinessDate(date);
        value.setCashReceived(totals.cashReceived());
        value.setCashPaid(totals.cashPaid());
        value.setNetCashMovement(totals.netCashMovement());
        value.setOpeningCashVerification(LEGACY_UNVERIFIED);
        value.setReason(reason);
        value.setResolvedBy(resolver.getId());
        value.setResolvedAt(LocalDateTime.now());
        value.setIdempotencyKey(key);

        LegacyCashActorResolution saved;
        try {
            saved = resolutions.saveAndFlush(value);
        } catch (DataIntegrityViolationException exception) {
            throw new ResourceConflictException(
                    "Legacy cash-activity resolution already exists or the idempotency key is in use.");
        }
        audit.log("LEGACY_CASH_ACTOR_BUCKET_RESOLVED", "CASHIER_RECONCILIATION",
                saved.getId(), resolver.getId(),
                "targetActorId=" + targetUserId + ", targetUsername=" + target.getUsername()
                        + ", businessDate=" + date + ", cashReceived=" + totals.cashReceived()
                        + ", cashPaid=" + totals.cashPaid() + ", netCashMovement="
                        + totals.netCashMovement() + ", openingCashVerification="
                        + LEGACY_UNVERIFIED + ", reason=" + reason + ", idempotencyKey=" + key);
        return response(saved, target);
    }

    @Transactional(readOnly = true)
    public List<LegacyCashActorResolutionResponse> getCurrent() {
        Role role = currentUserRole.getCurrentRole().orElse(null);
        if (role != Role.DIRECTOR && role != Role.SUPER_ADMIN) {
            throw new RuntimeException("Legacy cash-activity resolutions are restricted.");
        }
        return businessDates.getCurrentOpenBusinessDate()
                .map(date -> resolutions.findByBusinessDateOrderByResolvedAtDesc(date.getBusinessDate())
                        .stream().map(value -> response(value, requiredUser(value.getActorUserId())))
                        .toList())
                .orElse(List.of());
    }

    CashTotals calculateCashTotals(UUID actorId, LocalDate date) {
        List<ChipBuyIn> receivedRows = buyIns.findByBusinessDateAndCreatedBy(date, actorId)
                .stream().filter(value -> isCash(value.getPaymentMode())).toList();
        List<ChipCashOut> cashOutRows = cashOuts.findByBusinessDateAndCreatedBy(date, actorId)
                .stream().filter(value -> isCash(value.getPaymentMode())).toList();
        List<LosingReturn> losingReturnRows = losingReturns
                .findByBusinessDateAndCreatedBy(date, actorId).stream()
                .filter(value -> isCash(value.getPaymentMode())).toList();
        BigDecimal received = receivedRows.stream().map(ChipBuyIn::getAmountReceived)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paid = cashOutRows.stream().map(ChipCashOut::getCashPaid)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(losingReturnRows.stream().map(LosingReturn::getAmountPaid)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal net = received.subtract(paid);
        if (received.compareTo(MAX_AMOUNT) > 0 || paid.compareTo(MAX_AMOUNT) > 0
                || net.abs().compareTo(MAX_AMOUNT) > 0) {
            throw new ResourceConflictException(
                    "Persisted CASH activity totals exceed supported accounting limits.");
        }
        return new CashTotals(received, paid, net,
                receivedRows.size() + cashOutRows.size() + losingReturnRows.size());
    }

    private boolean isCash(String paymentMode) {
        return "CASH".equalsIgnoreCase(paymentMode == null ? "" : paymentMode.trim());
    }

    private void validateSuperAdmin() {
        if (!currentUserRole.getCurrentRole()
                .map(permissions::canResolveLegacyCashActorBucket).orElse(false)) {
            throw new RuntimeException("Only Super Admin can resolve legacy cash-activity buckets.");
        }
    }

    private void validateRequest(LegacyCashActorResolutionRequest request) {
        if (request == null || request.reason() == null
                || request.reason().trim().length() < 20
                || request.reason().trim().length() > 500) {
            throw new IllegalArgumentException(
                    "Resolution reason must be between 20 and 500 characters.");
        }
        if (request.idempotencyKey() == null || request.idempotencyKey().trim().isEmpty()
                || request.idempotencyKey().trim().length() > 100) {
            throw new IllegalArgumentException(
                    "Idempotency key is required and must not exceed 100 characters.");
        }
    }

    private void validateExactReplay(
            LegacyCashActorResolution existing, UUID targetUserId, String reason) {
        if (!existing.getActorUserId().equals(targetUserId)
                || !existing.getReason().equals(reason)) {
            throw new ResourceConflictException(
                    "Idempotency key is already associated with a different legacy resolution.");
        }
    }

    private User requiredUser(UUID id) {
        return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("Target user not found."));
    }

    private LegacyCashActorResolutionResponse response(LegacyCashActorResolution value, User target) {
        return new LegacyCashActorResolutionResponse(value.getId(), value.getActorUserId(),
                target.getUsername(), value.getBusinessDate(), value.getCashReceived(),
                value.getCashPaid(), value.getNetCashMovement(), value.getOpeningCashVerification(),
                LEGACY_RESOLVED, null, null, value.getReason(), value.getResolvedBy(),
                value.getResolvedAt());
    }

    record CashTotals(BigDecimal cashReceived, BigDecimal cashPaid,
                      BigDecimal netCashMovement, int transactionCount) {
    }
}
