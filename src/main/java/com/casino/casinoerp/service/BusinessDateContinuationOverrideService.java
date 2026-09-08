package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.BusinessDateContinuationOverrideResponse;
import com.casino.casinoerp.dto.BusinessDateHealthResponse;
import com.casino.casinoerp.dto.CreateBusinessDateContinuationOverrideRequest;
import com.casino.casinoerp.dto.RevokeBusinessDateContinuationOverrideRequest;
import com.casino.casinoerp.entity.AuditLog;
import com.casino.casinoerp.entity.BusinessDateContinuationOverride;
import com.casino.casinoerp.entity.BusinessDateHealth;
import com.casino.casinoerp.entity.User;
import com.casino.casinoerp.exception.ResourceConflictException;
import com.casino.casinoerp.repository.AuditLogRepository;
import com.casino.casinoerp.repository.BusinessDateContinuationOverrideRepository;
import com.casino.casinoerp.repository.BusinessDateRepository;
import com.casino.casinoerp.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

@Service
public class BusinessDateContinuationOverrideService {
    public static final int MIN_DURATION_MINUTES = 1;
    public static final int MAX_DURATION_MINUTES = 60;
    private static final int MAX_REASON_LENGTH = 500;
    private static final String EXPIRED_REPLACEMENT_REASON = "SYSTEM_EXPIRED_REPLACEMENT";
    private static final ZoneId CASINO_TIME_ZONE = ZoneId.of("Asia/Kathmandu");

    private final BusinessDateContinuationOverrideRepository overrides;
    private final BusinessDateRepository businessDates;
    private final BusinessDateService businessDateService;
    private final AuthenticatedUserService authenticatedUsers;
    private final RolePermissionService permissions;
    private final UserRepository users;
    private final AuditLogRepository auditLogs;
    private final Clock clock;

    public BusinessDateContinuationOverrideService(
            BusinessDateContinuationOverrideRepository overrides,
            BusinessDateRepository businessDates,
            BusinessDateService businessDateService,
            AuthenticatedUserService authenticatedUsers,
            RolePermissionService permissions,
            UserRepository users,
            AuditLogRepository auditLogs,
            Clock clock) {
        this.overrides = overrides;
        this.businessDates = businessDates;
        this.businessDateService = businessDateService;
        this.authenticatedUsers = authenticatedUsers;
        this.permissions = permissions;
        this.users = users;
        this.auditLogs = auditLogs;
        this.clock = clock;
    }

    @Transactional
    public BusinessDateContinuationOverrideResponse create(CreateBusinessDateContinuationOverrideRequest request) {
        User actor = requireManager();
        String reason = requiredReason(request.reason(), "Continuation reason");
        int duration = requiredDuration(request.durationMinutes());
        businessDates.acquireLifecycleLock();
        BusinessDateHealthResponse health = requireEligibleStaleDate();
        Instant now = clock.instant();

        overrides.findByBusinessDateAndRevokedAtIsNull(health.businessDate()).ifPresent(existing -> {
            if (isEffective(existing, now)) {
                throw new ResourceConflictException("An active continuation override already exists for this Business Date.");
            }
            revokeRecord(existing, actor.getId(), now, EXPIRED_REPLACEMENT_REASON);
            audit("EXPIRE_BUSINESS_DATE_CONTINUATION_OVERRIDE", existing, actor.getId(),
                    "businessDate=" + existing.getBusinessDate() + "; expiredAt=" + existing.getExpiresAt()
                            + "; replacementAuthorizedBy=" + actor.getId());
        });

        BusinessDateContinuationOverride value = new BusinessDateContinuationOverride();
        value.setBusinessDate(health.businessDate());
        value.setReason(reason);
        value.setAuthorizedBy(actor.getId());
        value.setAuthorizedAt(now);
        value.setExpiresAt(now.plus(Duration.ofMinutes(duration)));
        BusinessDateContinuationOverride saved = overrides.saveAndFlush(value);
        audit("CREATE_BUSINESS_DATE_CONTINUATION_OVERRIDE", saved, actor.getId(),
                "businessDate=" + saved.getBusinessDate() + "; durationMinutes=" + duration
                        + "; expiresAt=" + saved.getExpiresAt() + "; reason=" + reason);
        return response(saved, now, true);
    }

    @Transactional
    public BusinessDateContinuationOverrideResponse revoke(RevokeBusinessDateContinuationOverrideRequest request) {
        User actor = requireManager();
        String reason = requiredReason(request.reason(), "Revocation reason");
        businessDates.acquireLifecycleLock();
        BusinessDateHealthResponse health = businessDateService.getHealth();
        if (health.businessDate() == null || health.health() != BusinessDateHealth.STALE) {
            throw new ResourceConflictException("No stale OPEN Business Date has a continuation override to revoke.");
        }
        BusinessDateContinuationOverride value = overrides
                .findByBusinessDateAndRevokedAtIsNull(health.businessDate())
                .orElseThrow(() -> new ResourceConflictException(
                        "No applicable continuation override exists for the current Business Date."));
        Instant now = clock.instant();
        if (!isEffective(value, now)) {
            throw new ResourceConflictException("The continuation override has already expired.");
        }
        revokeRecord(value, actor.getId(), now, reason);
        audit("REVOKE_BUSINESS_DATE_CONTINUATION_OVERRIDE", value, actor.getId(),
                "businessDate=" + value.getBusinessDate() + "; revokedAt=" + now + "; reason=" + reason);
        return response(value, now, false);
    }

    @Transactional(readOnly = true)
    public Optional<BusinessDateContinuationOverrideResponse> current() {
        BusinessDateHealthResponse health = businessDateService.getHealth();
        if (health.businessDate() == null) return Optional.empty();
        Instant now = clock.instant();
        boolean eligibleState = isBeyondGraceStale(health);
        return overrides.findByBusinessDateAndRevokedAtIsNull(health.businessDate())
                .map(value -> response(value, now, eligibleState));
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Optional<BusinessDateContinuationOverride> activeFor(LocalDate businessDate) {
        Instant now = clock.instant();
        BusinessDateHealthResponse health = businessDateService.getHealth();
        if (!businessDate.equals(health.businessDate()) || !isBeyondGraceStale(health)) {
            return Optional.empty();
        }
        return overrides.findByBusinessDateAndRevokedAtIsNull(businessDate)
                .filter(value -> isEffective(value, now));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void auditOperationUnderOverride(BusinessDateContinuationOverride value) {
        audit("BUSINESS_DATE_OPERATION_UNDER_CONTINUATION_OVERRIDE", value,
                authenticatedUsers.getRequiredUser().getId(),
                "businessDate=" + value.getBusinessDate() + "; overrideId=" + value.getId()
                        + "; expiresAt=" + value.getExpiresAt());
    }

    private BusinessDateHealthResponse requireEligibleStaleDate() {
        BusinessDateHealthResponse health = businessDateService.getHealth();
        if (health.health() != BusinessDateHealth.STALE) {
            throw new ResourceConflictException(
                    "A continuation override may only be created for the current stale OPEN Business Date.");
        }
        if (!isBeyondGraceStale(health)) {
            throw new ResourceConflictException(
                    "The stale Business Date is still within the normal operational grace window.");
        }
        return health;
    }

    private boolean isBeyondGraceStale(BusinessDateHealthResponse health) {
        return health.health() == BusinessDateHealth.STALE
                && (health.staleByDays() != 1
                || businessDateService.currentCasinoDateTime().toLocalTime()
                .isAfter(businessDateService.getStaleOperationGraceEnd()));
    }

    private User requireManager() {
        User actor = authenticatedUsers.getRequiredUser();
        if (!permissions.canManageBusinessDate(actor.getRole())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Only Director or Super Admin may manage continuation overrides.");
        }
        return actor;
    }

    private int requiredDuration(Integer duration) {
        if (duration == null || duration < MIN_DURATION_MINUTES || duration > MAX_DURATION_MINUTES) {
            throw new IllegalArgumentException("Duration must be between 1 and 60 minutes.");
        }
        return duration;
    }

    private String requiredReason(String reason, String label) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        String normalized = reason.trim();
        if (normalized.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException(label + " must not exceed 500 characters.");
        }
        return normalized;
    }

    private boolean isEffective(BusinessDateContinuationOverride value, Instant now) {
        return value.getRevokedAt() == null && now.isBefore(value.getExpiresAt());
    }

    private void revokeRecord(BusinessDateContinuationOverride value, UUID actorId, Instant now, String reason) {
        value.setRevokedAt(now);
        value.setRevokedBy(actorId);
        value.setRevokeReason(reason);
        overrides.saveAndFlush(value);
    }

    private void audit(String action, BusinessDateContinuationOverride value, UUID actorId, String remarks) {
        AuditLog log = new AuditLog();
        log.setBusinessDate(value.getBusinessDate());
        log.setActionType(action);
        log.setModuleName("BUSINESS_DATE");
        log.setEntityId(value.getId());
        log.setPerformedBy(actorId);
        log.setPerformedAt(LocalDateTime.ofInstant(clock.instant(), CASINO_TIME_ZONE));
        log.setRemarks(remarks);
        auditLogs.save(log);
    }

    private BusinessDateContinuationOverrideResponse response(
            BusinessDateContinuationOverride value, Instant now, boolean eligibleState) {
        return new BusinessDateContinuationOverrideResponse(
                value.getId(), value.getBusinessDate(), value.getReason(), actor(value.getAuthorizedBy()),
                value.getAuthorizedAt(), value.getExpiresAt(), eligibleState && isEffective(value, now),
                !now.isBefore(value.getExpiresAt()), terminationType(value, now), value.getRevokedAt(),
                actor(value.getRevokedBy()), value.getRevokeReason());
    }

    private String terminationType(BusinessDateContinuationOverride value, Instant now) {
        if (EXPIRED_REPLACEMENT_REASON.equals(value.getRevokeReason())) return "EXPIRED_REPLACED";
        if (value.getRevokedAt() != null) return "REVOKED";
        if (!now.isBefore(value.getExpiresAt())) return "EXPIRED";
        return null;
    }

    private BusinessDateContinuationOverrideResponse.Actor actor(UUID id) {
        if (id == null) return null;
        return users.findById(id)
                .map(user -> new BusinessDateContinuationOverrideResponse.Actor(
                        user.getId(), user.getUsername(), user.getFullName()))
                .orElse(new BusinessDateContinuationOverrideResponse.Actor(id, null, null));
    }
}
