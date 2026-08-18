package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.RunningFundsReconciliationResponse;
import com.casino.casinoerp.dto.RunningFundsReportResponse;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.exception.ResourceNotFoundException;
import com.casino.casinoerp.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;

@Service
public class RunningFundsReportService {
    private final BusinessDateRepository businessDateRepository;
    private final BusinessDateService businessDateService;
    private final ChipBuyInRepository buyInRepository;
    private final ChipCashOutRepository cashOutRepository;
    private final LosingReturnRepository losingReturnRepository;
    private final VerifiedGamingResultRepository gamingResultRepository;
    private final CashierReconciliationRepository reconciliationRepository;
    private final UserRepository userRepository;
    private final SessionFinancialPositionService financialPositionService;
    private final CurrentUserRoleService currentUserRoleService;
    private final RolePermissionService rolePermissionService;

    public RunningFundsReportService(
            BusinessDateRepository businessDateRepository,
            BusinessDateService businessDateService,
            ChipBuyInRepository buyInRepository,
            ChipCashOutRepository cashOutRepository,
            LosingReturnRepository losingReturnRepository,
            VerifiedGamingResultRepository gamingResultRepository,
            CashierReconciliationRepository reconciliationRepository,
            UserRepository userRepository,
            SessionFinancialPositionService financialPositionService,
            CurrentUserRoleService currentUserRoleService,
            RolePermissionService rolePermissionService) {
        this.businessDateRepository = businessDateRepository;
        this.businessDateService = businessDateService;
        this.buyInRepository = buyInRepository;
        this.cashOutRepository = cashOutRepository;
        this.losingReturnRepository = losingReturnRepository;
        this.gamingResultRepository = gamingResultRepository;
        this.reconciliationRepository = reconciliationRepository;
        this.userRepository = userRepository;
        this.financialPositionService = financialPositionService;
        this.currentUserRoleService = currentUserRoleService;
        this.rolePermissionService = rolePermissionService;
    }

    @Transactional(readOnly = true)
    public RunningFundsReportResponse getReport(LocalDate requestedDate) {
        if (!currentUserRoleService.getCurrentRole().map(rolePermissionService::canViewRunningFundsReport).orElse(false)) {
            throw new RuntimeException("Access denied. Running Funds Report is restricted to management.");
        }

        LocalDate date = requestedDate == null
                ? businessDateService.getCurrentOpenBusinessDate()
                    .orElseThrow(() -> new IllegalStateException("Current business date is not opened."))
                    .getBusinessDate()
                : requestedDate;
        BusinessDate businessDate = businessDateRepository.findByBusinessDate(date)
                .orElseThrow(() -> new ResourceNotFoundException("Business Date not found."));

        List<ChipBuyIn> buyInRecords = buyInRepository.findByBusinessDate(date);
        List<ChipCashOut> cashOutRecords = cashOutRepository.findByBusinessDate(date);
        List<LosingReturn> losingReturnRecords = losingReturnRepository.findByBusinessDate(date);
        BigDecimal buyIns = sum(buyInRecords, ChipBuyIn::getAmountReceived);
        BigDecimal cashOuts = sum(cashOutRecords, ChipCashOut::getCashPaid);
        BigDecimal losingReturns = sum(losingReturnRecords, LosingReturn::getAmountPaid);
        List<VerifiedGamingResult> gamingResults = gamingResultRepository.findByBusinessDate(date);
        BigDecimal wins = sum(gamingResults.stream()
                .filter(value -> value.getResultType() == VerifiedGamingResultType.WIN).toList(), VerifiedGamingResult::getAmount);
        BigDecimal losses = sum(gamingResults.stream()
                .filter(value -> value.getResultType() == VerifiedGamingResultType.LOSS).toList(), VerifiedGamingResult::getAmount);

        List<CashierReconciliation> reconciliations = reconciliationRepository.findByBusinessDateOrderBySubmittedAtDesc(date);
        Map<UUID, User> users = userRepository.findAllById(reconciliations.stream()
                .map(CashierReconciliation::getCashierUserId).collect(java.util.stream.Collectors.toSet()))
                .stream().collect(java.util.stream.Collectors.toMap(User::getId, Function.identity()));
        List<RunningFundsReconciliationResponse> rows = reconciliations.stream().map(value -> {
            User cashier = users.get(value.getCashierUserId());
            BigDecimal received = sum(buyInRecords.stream()
                    .filter(record -> value.getCashierUserId().equals(record.getCreatedBy())).toList(), ChipBuyIn::getAmountReceived);
            BigDecimal paid = sum(cashOutRecords.stream()
                    .filter(record -> value.getCashierUserId().equals(record.getCreatedBy())).toList(), ChipCashOut::getCashPaid)
                    .add(sum(losingReturnRecords.stream()
                            .filter(record -> value.getCashierUserId().equals(record.getCreatedBy())).toList(), LosingReturn::getAmountPaid));
            return new RunningFundsReconciliationResponse(value.getId(),
                    cashier == null ? null : cashier.getUsername(), cashier == null ? null : cashier.getFullName(),
                    received, paid, value.getExpectedClosingCash(), value.getActualClosingCash(), value.getVariance(),
                    value.getStatus(), value.getLifecycleStatus());
        }).toList();

        int submitted = (int) reconciliations.stream().filter(value -> "SUBMITTED".equals(value.getLifecycleStatus())).count();
        int reopened = (int) reconciliations.stream().filter(value -> "REOPENED".equals(value.getLifecycleStatus())).count();
        long activeCashiers = userRepository.findAll().stream()
                .filter(value -> "ACTIVE".equalsIgnoreCase(value.getStatus()))
                .filter(value -> com.casino.casinoerp.security.Role.fromValue(value.getRole())
                        .filter(role -> role == com.casino.casinoerp.security.Role.CASHIER).isPresent())
                .count();
        int unresolved = Math.toIntExact(Math.max(0, activeCashiers - submitted));
        BigDecimal aggregateVariance = sum(reconciliations.stream()
                .filter(value -> "SUBMITTED".equals(value.getLifecycleStatus())).toList(), CashierReconciliation::getVariance);

        return new RunningFundsReportResponse(date, businessDate.getStatus(), LocalDateTime.now(),
                buyIns, cashOuts, losingReturns, buyIns.subtract(cashOuts).subtract(losingReturns),
                wins, losses, losses.subtract(wins), financialPositionService.getOutstandingPosition(date),
                submitted, reopened, unresolved, aggregateVariance, rows);
    }

    private <T> BigDecimal sum(List<T> values, Function<T, BigDecimal> amount) {
        return values.stream().map(amount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
