package com.casino.casinoerp.service;

import com.casino.casinoerp.entity.BusinessDate;
import com.casino.casinoerp.entity.BusinessDateContinuationOverride;
import com.casino.casinoerp.entity.BusinessDateHealth;
import com.casino.casinoerp.dto.BusinessDateHealthResponse;
import com.casino.casinoerp.exception.ResourceConflictException;
import com.casino.casinoerp.repository.BusinessDateRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
public class BusinessDateService {

    private static final LocalTime BUSINESS_DAY_START =
            LocalTime.of(9, 0);
    private static final LocalTime STALE_OPERATION_GRACE_END =
            LocalTime.of(12, 30);
    private static final ZoneId CASINO_TIME_ZONE =
            ZoneId.of("Asia/Kathmandu");

    private final BusinessDateRepository businessDateRepository;
    private final BusinessDateValidationService validationService;
    private final AuditLogService auditLogService;
    private final RolePermissionService rolePermissionService;
    private final CurrentUserRoleService currentUserRoleService;
    private final Clock clock;
    private BusinessDateContinuationOverrideService continuationOverrides;

    public BusinessDateService(
            BusinessDateRepository businessDateRepository,
            @Lazy BusinessDateValidationService validationService,
            @Lazy AuditLogService auditLogService,
            RolePermissionService rolePermissionService,
            CurrentUserRoleService currentUserRoleService,
            Clock clock) {

        this.businessDateRepository = businessDateRepository;
        this.validationService = validationService;
        this.auditLogService = auditLogService;
        this.rolePermissionService = rolePermissionService;
        this.currentUserRoleService = currentUserRoleService;
        this.clock = clock;
    }

    @Autowired(required = false)
    public void setContinuationOverrides(@Lazy BusinessDateContinuationOverrideService continuationOverrides) {
        this.continuationOverrides = continuationOverrides;
    }

    public LocalDate getCurrentBusinessDate() {
        return resolveBusinessDate(currentCasinoDateTime());
    }

    public LocalDate resolveBusinessDate(LocalDateTime dateTime) {
        List<BusinessDate> openDates = businessDateRepository.findByStatus("OPEN");

        if (openDates.size() > 1) {
            throw new IllegalStateException("Business Date state is inconsistent: multiple OPEN dates exist.");
        }
        if (openDates.size() == 1) {
            return openDates.get(0).getBusinessDate();
        }

        return calculateBusinessDate(dateTime);
    }

    public LocalDate resolveAttendanceBusinessDate(Instant attendanceTime) {
        LocalDateTime casinoTime = LocalDateTime.ofInstant(attendanceTime, CASINO_TIME_ZONE);
        return calculateBusinessDate(casinoTime);
    }

    public LocalDate getExpectedBusinessDate() {
        return calculateBusinessDate(currentCasinoDateTime());
    }

    public LocalDateTime currentCasinoDateTime() {
        return LocalDateTime.ofInstant(clock.instant(), CASINO_TIME_ZONE);
    }

    public BusinessDateHealthResponse getHealth() {
        LocalDate expected = getExpectedBusinessDate();
        List<BusinessDate> openDates = businessDateRepository.findByStatus("OPEN");

        if (openDates.isEmpty()) {
            return new BusinessDateHealthResponse(null, expected, BusinessDateHealth.MISSING,
                    false, 0, "No operational Business Date is OPEN.");
        }
        if (openDates.size() > 1) {
            return new BusinessDateHealthResponse(null, expected, BusinessDateHealth.INCONSISTENT,
                    false, 0, "Multiple operational Business Dates are OPEN.");
        }

        LocalDate open = openDates.get(0).getBusinessDate();
        if (open == null) {
            return new BusinessDateHealthResponse(null, expected, BusinessDateHealth.INCONSISTENT,
                    false, 0, "The OPEN Business Date has no business-date value.");
        }
        if (open.isAfter(expected)) {
            return new BusinessDateHealthResponse(open, expected, BusinessDateHealth.INCONSISTENT,
                    false, 0, "The OPEN Business Date is later than the expected Business Date.");
        }
        if (open.isBefore(expected)) {
            long staleByDays = ChronoUnit.DAYS.between(open, expected);
            return new BusinessDateHealthResponse(open, expected, BusinessDateHealth.STALE,
                    true, staleByDays, "The OPEN Business Date is earlier than the expected Business Date.");
        }
        return new BusinessDateHealthResponse(open, expected, BusinessDateHealth.HEALTHY,
                false, 0, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void validateNewOperationalMutationAllowed() {
        businessDateRepository.acquireLifecycleLock();
        BusinessDateHealthResponse health = getHealth();
        switch (health.health()) {
            case HEALTHY -> { }
            case STALE -> {
                if (health.staleByDays() != 1
                        || currentCasinoDateTime().toLocalTime().isAfter(STALE_OPERATION_GRACE_END)) {
                    var activeOverride = continuationOverrides == null
                            ? Optional.<BusinessDateContinuationOverride>empty()
                            : continuationOverrides.activeFor(health.businessDate());
                    if (activeOverride.isEmpty()) {
                        throw staleOperationConflict(health.businessDate());
                    }
                    continuationOverrides.auditOperationUnderOverride(activeOverride.get());
                }
            }
            case MISSING -> throw new ResourceConflictException(
                    "No operational Business Date is OPEN. New operations are disabled.");
            case INCONSISTENT -> throw new ResourceConflictException(
                    "Business Date state is inconsistent. New operations are disabled until it is resolved.");
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void validateSettlementMutationAllowed() {
        businessDateRepository.acquireLifecycleLock();
        BusinessDateHealthResponse health = getHealth();
        switch (health.health()) {
            case HEALTHY, STALE -> { }
            case MISSING -> throw new ResourceConflictException(
                    "No operational Business Date is OPEN. Settlement operations requiring an OPEN Business Date are disabled.");
            case INCONSISTENT -> throw new ResourceConflictException(
                    "Business Date state is inconsistent. Settlement operations are disabled until it is resolved.");
        }
    }

    public LocalTime getStaleOperationGraceEnd() {
        return STALE_OPERATION_GRACE_END;
    }

    private ResourceConflictException staleOperationConflict(LocalDate openBusinessDate) {
        return new ResourceConflictException("Operational Business Date " + openBusinessDate
                + " is stale. New operations are disabled until the Business Date is resolved.");
    }

    private LocalDate calculateBusinessDate(LocalDateTime dateTime) {
        if (dateTime.toLocalTime().isBefore(BUSINESS_DAY_START)) {
            return dateTime.toLocalDate().minusDays(1);
        }

        return dateTime.toLocalDate();
    }

    public void validateBusinessDateIsOpen() {
        LocalDate currentBusinessDate = getCurrentBusinessDate();

        BusinessDate bd = businessDateRepository
                .findByBusinessDate(currentBusinessDate)
                .orElseThrow(() -> new RuntimeException("Current business date is not opened."));

        if (!"OPEN".equalsIgnoreCase(bd.getStatus())) {
            throw new RuntimeException("Current business date is not OPEN.");
        }
    }

    public List<BusinessDate> getAll() {
        return businessDateRepository.findAll();
    }

    public Optional<BusinessDate> getCurrentOpenBusinessDate() {
        List<BusinessDate> openDates = businessDateRepository.findByStatus("OPEN");

        if (openDates.isEmpty()) {
            return Optional.empty();
        }
        if (openDates.size() > 1) {
            throw new IllegalStateException("Business Date state is inconsistent: multiple OPEN dates exist.");
        }
        return Optional.of(openDates.get(0));
    }

    @Transactional
    public BusinessDate openBusinessDate(LocalDate businessDate, String remarks) {

        validateBusinessDateLifecycleRole();
        businessDateRepository.acquireLifecycleLock();

        if (!businessDateRepository.findByStatus("OPEN").isEmpty()) {
            throw new ResourceConflictException("Another business date is already OPEN.");
        }

        if (businessDateRepository.findByBusinessDate(businessDate).isPresent()) {
            throw new ResourceConflictException("Business date already exists.");
        }

        BusinessDate bd = new BusinessDate();
        bd.setBusinessDate(businessDate);
        bd.setStatus("OPEN");
        bd.setOpenedAt(currentCasinoDateTime());
        bd.setRemarks(remarks);

        BusinessDate saved = saveLifecycleChange(bd);

        auditLogService.log(
                "OPEN_BUSINESS_DATE",
                "BUSINESS_DATE",
                saved.getId(),
                null,
                "Business date opened: " + saved.getBusinessDate()
        );

        return saved;
    }

    @Transactional
    public BusinessDate closeBusinessDate(LocalDate businessDate) {

        validateBusinessDateLifecycleRole();
        businessDateRepository.acquireLifecycleLock();

        BusinessDate bd = businessDateRepository
                .findByBusinessDateForUpdate(businessDate)
                .orElseThrow(() -> new RuntimeException("Business date not found."));

        if ("CLOSED".equalsIgnoreCase(bd.getStatus())) {
            throw new ResourceConflictException("Business date is already CLOSED.");
        }

        List<String> errors = validationService.validateCloseRequirements(businessDate);

        if (!errors.isEmpty()) {
            throw new RuntimeException(String.join(" | ", errors));
        }

        bd.setStatus("CLOSED");
        bd.setClosedAt(currentCasinoDateTime());

        BusinessDate saved = saveLifecycleChange(bd);

        auditLogService.log(
                "CLOSE_BUSINESS_DATE",
                "BUSINESS_DATE",
                saved.getId(),
                null,
                "Business date closed: " + saved.getBusinessDate()
        );

        return saved;
    }

    @Transactional
    public BusinessDate reopenBusinessDate(LocalDate businessDate, String remarks) {

        validateBusinessDateLifecycleRole();
        businessDateRepository.acquireLifecycleLock();

        if (!businessDateRepository.findByStatus("OPEN").isEmpty()) {
            throw new ResourceConflictException("Another business date is already OPEN.");
        }

        BusinessDate bd = businessDateRepository
                .findByBusinessDateForUpdate(businessDate)
                .orElseThrow(() -> new RuntimeException("Business date not found."));

        if ("OPEN".equalsIgnoreCase(bd.getStatus())) {
            throw new ResourceConflictException("Business date is already OPEN.");
        }

        bd.setStatus("OPEN");
        bd.setClosedAt(null);
        bd.setRemarks(remarks);

        BusinessDate saved = saveLifecycleChange(bd);

        auditLogService.log(
                "REOPEN_BUSINESS_DATE",
                "BUSINESS_DATE",
                saved.getId(),
                currentUserRoleService.getCurrentUserId(),
                "Business date reopened: " + saved.getBusinessDate()
        );

        return saved;
    }

    private BusinessDate saveLifecycleChange(BusinessDate businessDate) {
        try {
            return businessDateRepository.saveAndFlush(businessDate);
        } catch (DataIntegrityViolationException exception) {
            throw new ResourceConflictException(
                    "Business Date lifecycle changed concurrently. Refresh and retry.");
        }
    }

    private void validateBusinessDateLifecycleRole() {
        if (!currentUserRoleService.getCurrentRole()
                .map(rolePermissionService::canManageBusinessDate)
                .orElse(false)) {
            throw new RuntimeException(
                    "Access denied. Only Director or Super Admin can manage business dates."
            );
        }
    }
}
