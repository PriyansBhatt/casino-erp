package com.casino.casinoerp.service;

import com.casino.casinoerp.entity.BusinessDate;
import com.casino.casinoerp.repository.BusinessDateRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Service
public class BusinessDateService {

    private static final LocalTime BUSINESS_DAY_START =
            LocalTime.of(9, 0);
    private static final ZoneId CASINO_TIME_ZONE =
            ZoneId.of("Asia/Kathmandu");

    private final BusinessDateRepository businessDateRepository;
    private final BusinessDateValidationService validationService;
    private final AuditLogService auditLogService;
    private final RolePermissionService rolePermissionService;
    private final CurrentUserRoleService currentUserRoleService;

    public BusinessDateService(
            BusinessDateRepository businessDateRepository,
            @Lazy BusinessDateValidationService validationService,
            @Lazy AuditLogService auditLogService,
            RolePermissionService rolePermissionService,
            CurrentUserRoleService currentUserRoleService) {

        this.businessDateRepository = businessDateRepository;
        this.validationService = validationService;
        this.auditLogService = auditLogService;
        this.rolePermissionService = rolePermissionService;
        this.currentUserRoleService = currentUserRoleService;
    }

    public LocalDate getCurrentBusinessDate() {
        return resolveBusinessDate(LocalDateTime.now());
    }

    public LocalDate resolveBusinessDate(LocalDateTime dateTime) {
        List<BusinessDate> openDates = businessDateRepository.findByStatus("OPEN");

        if (!openDates.isEmpty()) {
            return openDates.get(0).getBusinessDate();
        }

        return calculateBusinessDate(dateTime);
    }

    public LocalDate resolveAttendanceBusinessDate(Instant attendanceTime) {
        LocalDateTime casinoTime = LocalDateTime.ofInstant(attendanceTime, CASINO_TIME_ZONE);
        return calculateBusinessDate(casinoTime);
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

        return Optional.of(openDates.get(0));
    }

    public BusinessDate openBusinessDate(LocalDate businessDate, String remarks) {

        validateBusinessDateLifecycleRole();

        if (!businessDateRepository.findByStatus("OPEN").isEmpty()) {
            throw new RuntimeException("Another business date is already OPEN.");
        }

        if (businessDateRepository.findByBusinessDate(businessDate).isPresent()) {
            throw new RuntimeException("Business date already exists.");
        }

        BusinessDate bd = new BusinessDate();
        bd.setBusinessDate(businessDate);
        bd.setStatus("OPEN");
        bd.setOpenedAt(LocalDateTime.now());
        bd.setRemarks(remarks);

        BusinessDate saved = businessDateRepository.save(bd);

        auditLogService.log(
                "OPEN_BUSINESS_DATE",
                "BUSINESS_DATE",
                saved.getId(),
                null,
                "Business date opened: " + saved.getBusinessDate()
        );

        return saved;
    }

    public BusinessDate closeBusinessDate(LocalDate businessDate) {

        validateBusinessDateLifecycleRole();

        BusinessDate bd = businessDateRepository
                .findByBusinessDate(businessDate)
                .orElseThrow(() -> new RuntimeException("Business date not found."));

        if ("CLOSED".equalsIgnoreCase(bd.getStatus())) {
            throw new RuntimeException("Business date is already CLOSED.");
        }

        List<String> errors = validationService.validateCloseRequirements(businessDate);

        if (!errors.isEmpty()) {
            throw new RuntimeException(String.join(" | ", errors));
        }

        bd.setStatus("CLOSED");
        bd.setClosedAt(LocalDateTime.now());

        BusinessDate saved = businessDateRepository.save(bd);

        auditLogService.log(
                "CLOSE_BUSINESS_DATE",
                "BUSINESS_DATE",
                saved.getId(),
                null,
                "Business date closed: " + saved.getBusinessDate()
        );

        return saved;
    }

    public BusinessDate reopenBusinessDate(LocalDate businessDate, String remarks) {

        validateBusinessDateLifecycleRole();

        if (!businessDateRepository.findByStatus("OPEN").isEmpty()) {
            throw new RuntimeException("Another business date is already OPEN.");
        }

        BusinessDate bd = businessDateRepository
                .findByBusinessDate(businessDate)
                .orElseThrow(() -> new RuntimeException("Business date not found."));

        if ("OPEN".equalsIgnoreCase(bd.getStatus())) {
            throw new RuntimeException("Business date is already OPEN.");
        }

        bd.setStatus("OPEN");
        bd.setClosedAt(null);
        bd.setRemarks(remarks);

        BusinessDate saved = businessDateRepository.save(bd);

        auditLogService.log(
                "REOPEN_BUSINESS_DATE",
                "BUSINESS_DATE",
                saved.getId(),
                currentUserRoleService.getCurrentUserId(),
                "Business date reopened: " + saved.getBusinessDate()
        );

        return saved;
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
