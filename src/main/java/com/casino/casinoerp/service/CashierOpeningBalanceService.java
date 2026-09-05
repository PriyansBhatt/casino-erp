package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.CashierOpeningBalanceResponse;
import com.casino.casinoerp.dto.CreateCashierOpeningBalanceRequest;
import com.casino.casinoerp.entity.BusinessDate;
import com.casino.casinoerp.entity.CashierOpeningBalance;
import com.casino.casinoerp.entity.User;
import com.casino.casinoerp.exception.ResourceConflictException;
import com.casino.casinoerp.repository.CashierOpeningBalanceRepository;
import com.casino.casinoerp.repository.ChipBuyInRepository;
import com.casino.casinoerp.repository.ChipCashOutRepository;
import com.casino.casinoerp.repository.LosingReturnRepository;
import com.casino.casinoerp.security.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class CashierOpeningBalanceService {
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999999999999.99");
    private final CashierOpeningBalanceRepository repository;
    private final ChipBuyInRepository buyIns;
    private final ChipCashOutRepository cashOuts;
    private final LosingReturnRepository losingReturns;
    private final BusinessDateService businessDateService;
    private final AuthenticatedUserService authenticatedUserService;
    private final CurrentUserRoleService currentUserRoleService;
    private final SystemLockService systemLockService;
    private final AuditLogService auditLogService;

    public CashierOpeningBalanceService(CashierOpeningBalanceRepository repository,
            ChipBuyInRepository buyIns, ChipCashOutRepository cashOuts,
            LosingReturnRepository losingReturns, BusinessDateService businessDateService,
            AuthenticatedUserService authenticatedUserService,
            CurrentUserRoleService currentUserRoleService, SystemLockService systemLockService,
            AuditLogService auditLogService) {
        this.repository = repository;
        this.buyIns = buyIns;
        this.cashOuts = cashOuts;
        this.losingReturns = losingReturns;
        this.businessDateService = businessDateService;
        this.authenticatedUserService = authenticatedUserService;
        this.currentUserRoleService = currentUserRoleService;
        this.systemLockService = systemLockService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public CashierOpeningBalanceResponse getCurrent() {
        validateViewRole();
        User actor = authenticatedUserService.getRequiredUser();
        LocalDate date = currentOpenBusinessDate();
        return repository.findByCashierUserIdAndBusinessDate(actor.getId(), date)
                .map(value -> response(value, actor)).orElse(null);
    }

    @Transactional
    public CashierOpeningBalanceResponse create(CreateCashierOpeningBalanceRequest request) {
        validateCreateRole();
        if (request.openingCashAmount() == null || request.openingCashAmount().signum() < 0
                || request.openingCashAmount().compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException("Opening Cash must be zero or greater and within supported limits.");
        }
        if (systemLockService.isSystemLocked()) {
            throw new ResourceConflictException("System is locked. Cashier opening balance cannot be established.");
        }
        User actor = authenticatedUserService.getRequiredUser();
        LocalDate date = currentOpenBusinessDate();
        if (repository.findByCashierUserIdAndBusinessDate(actor.getId(), date).isPresent()) {
            throw new ResourceConflictException("Opening Cash has already been established for this cashier and Business Date.");
        }
        if (!buyIns.findByBusinessDateAndCreatedBy(date, actor.getId()).isEmpty()
                || !cashOuts.findByBusinessDateAndCreatedBy(date, actor.getId()).isEmpty()
                || !losingReturns.findByBusinessDateAndCreatedBy(date, actor.getId()).isEmpty()) {
            throw new ResourceConflictException("Opening Cash cannot be established after financial activity has been posted for this Business Date.");
        }

        LocalDateTime now = LocalDateTime.now();
        CashierOpeningBalance value = new CashierOpeningBalance();
        value.setCashierUserId(actor.getId());
        value.setBusinessDate(date);
        value.setOpeningCashAmount(request.openingCashAmount());
        value.setCreatedBy(actor.getId());
        value.setCreatedAt(now);
        value.setUpdatedAt(now);
        CashierOpeningBalance saved;
        try {
            saved = repository.saveAndFlush(value);
        } catch (DataIntegrityViolationException exception) {
            throw new ResourceConflictException("Opening Cash has already been established for this cashier and Business Date.");
        }
        auditLogService.log("CASHIER_OPENING_BALANCE_CREATED", "CASHIER_OPENING_BALANCE",
                saved.getId(), actor.getId(), "cashier=" + actor.getId() + ", businessDate=" + date
                        + ", openingCash=" + saved.getOpeningCashAmount());
        return response(saved, actor);
    }

    @Transactional(readOnly = true)
    public Optional<CashierOpeningBalance> findFor(UUID cashierUserId, LocalDate businessDate) {
        return repository.findByCashierUserIdAndBusinessDate(cashierUserId, businessDate);
    }

    private LocalDate currentOpenBusinessDate() {
        BusinessDate value = businessDateService.getCurrentOpenBusinessDate()
                .orElseThrow(() -> new IllegalStateException("Current business date is not opened."));
        return value.getBusinessDate();
    }

    private void validateCreateRole() {
        Role role = currentUserRoleService.getCurrentRole().orElse(null);
        if (role != Role.CASHIER && role != Role.SUPER_ADMIN) {
            throw new RuntimeException("Access denied. Only Cashier or Super Admin can establish their own Opening Cash.");
        }
    }

    private void validateViewRole() {
        Role role = currentUserRoleService.getCurrentRole().orElse(null);
        if (role != Role.CASHIER && role != Role.DIRECTOR && role != Role.SUPER_ADMIN) {
            throw new RuntimeException("Access denied. Cashier Opening Cash is restricted.");
        }
    }

    private CashierOpeningBalanceResponse response(CashierOpeningBalance value, User cashier) {
        return new CashierOpeningBalanceResponse(value.getId(), value.getCashierUserId(),
                cashier.getUsername(), cashier.getFullName(), value.getBusinessDate(),
                value.getOpeningCashAmount(), value.getCreatedBy(), value.getCreatedAt(), value.getUpdatedAt());
    }
}
