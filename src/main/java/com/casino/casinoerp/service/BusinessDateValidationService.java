package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.SessionFinancialPositionResponse;
import com.casino.casinoerp.entity.CashierReconciliation;
import com.casino.casinoerp.entity.ChipBuyIn;
import com.casino.casinoerp.entity.ChipCashOut;
import com.casino.casinoerp.entity.CustomerSession;
import com.casino.casinoerp.entity.LegacyCashActorResolution;
import com.casino.casinoerp.entity.LosingReturn;
import com.casino.casinoerp.entity.PitTableCustomerAssignmentStatus;
import com.casino.casinoerp.entity.User;
import com.casino.casinoerp.repository.CashierReconciliationRepository;
import com.casino.casinoerp.repository.CashierOpeningBalanceRepository;
import com.casino.casinoerp.repository.ChipBuyInRepository;
import com.casino.casinoerp.repository.ChipCashOutRepository;
import com.casino.casinoerp.repository.CustomerSessionRepository;
import com.casino.casinoerp.repository.PitTableCustomerAssignmentRepository;
import com.casino.casinoerp.repository.PitTableRepository;
import com.casino.casinoerp.repository.UserRepository;
import com.casino.casinoerp.repository.LegacyCashActorResolutionRepository;
import com.casino.casinoerp.repository.LosingReturnRepository;
import com.casino.casinoerp.security.Role;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BusinessDateValidationService {

    private final CustomerSessionRepository sessionRepository;
    private final PitTableCustomerAssignmentRepository assignmentRepository;
    private final SessionFinancialPositionService financialPositionService;
    private final PitTableRepository pitTableRepository;
    private final CashierReconciliationRepository reconciliationRepository;
    private final UserRepository userRepository;
    private final CashierOpeningBalanceRepository openingBalanceRepository;
    private final ChipBuyInRepository buyInRepository;
    private final ChipCashOutRepository cashOutRepository;
    private final LosingReturnRepository losingReturnRepository;
    private final LegacyCashActorResolutionRepository legacyResolutionRepository;

    public BusinessDateValidationService(
            CustomerSessionRepository sessionRepository,
            PitTableCustomerAssignmentRepository assignmentRepository,
            SessionFinancialPositionService financialPositionService,
            PitTableRepository pitTableRepository,
            CashierReconciliationRepository reconciliationRepository,
            UserRepository userRepository,
            CashierOpeningBalanceRepository openingBalanceRepository,
            ChipBuyInRepository buyInRepository,
            ChipCashOutRepository cashOutRepository,
            LosingReturnRepository losingReturnRepository,
            LegacyCashActorResolutionRepository legacyResolutionRepository) {
        this.sessionRepository = sessionRepository;
        this.assignmentRepository = assignmentRepository;
        this.financialPositionService = financialPositionService;
        this.pitTableRepository = pitTableRepository;
        this.reconciliationRepository = reconciliationRepository;
        this.userRepository = userRepository;
        this.openingBalanceRepository = openingBalanceRepository;
        this.buyInRepository = buyInRepository;
        this.cashOutRepository = cashOutRepository;
        this.losingReturnRepository = losingReturnRepository;
        this.legacyResolutionRepository = legacyResolutionRepository;
    }

    public List<String> validateCloseRequirements(LocalDate businessDate) {
        List<String> errors = new ArrayList<>();
        List<CustomerSession> openSessions = sessionRepository
                .findByStatusIgnoreCaseAndBusinessDateOrderByEntryTimeAsc("OPEN", businessDate);

        if (!openSessions.isEmpty()) {
            errors.add(openSessions.size() + " customer session(s) still OPEN");
        }

        long activeAssignments = openSessions.stream()
                .filter(session -> assignmentRepository.findByCustomerSessionIdAndStatus(
                        session.getId(), PitTableCustomerAssignmentStatus.ACTIVE).isPresent())
                .count();
        if (activeAssignments > 0) {
            errors.add(activeAssignments + " OPEN customer session(s) still have an ACTIVE Pit Table assignment");
        }

        long positivePositions = 0;
        long negativePositions = 0;
        for (CustomerSession session : openSessions) {
            SessionFinancialPositionResponse position = financialPositionService.getPosition(session.getId());
            int comparison = position.calculatedChipPosition().compareTo(BigDecimal.ZERO);
            if (comparison > 0) positivePositions++;
            if (comparison < 0) negativePositions++;
        }
        if (positivePositions > 0) {
            errors.add(positivePositions + " OPEN customer session(s) have outstanding positive chip positions");
        }
        if (negativePositions > 0) {
            errors.add(negativePositions + " OPEN customer session(s) have inconsistent negative chip positions");
        }

        long openTables = pitTableRepository
                .findByStatusIgnoreCaseAndBusinessDate("OPEN", businessDate).size();
        if (openTables > 0) {
            errors.add(openTables + " Pit Table(s) still OPEN for the Business Date");
        }

        validateCashierReconciliations(businessDate, errors);
        validateLegacyNonCashierBuckets(businessDate, errors);
        return errors;
    }

    private void validateCashierReconciliations(LocalDate businessDate, List<String> errors) {
        List<User> activeCashiers = userRepository.findAll().stream()
                .filter(user -> "ACTIVE".equalsIgnoreCase(user.getStatus()))
                .filter(user -> Role.fromValue(user.getRole())
                        .filter(role -> role == Role.CASHIER).isPresent())
                .toList();
        Map<UUID, CashierReconciliation> byCashier = reconciliationRepository
                .findByBusinessDateOrderBySubmittedAtDesc(businessDate).stream()
                .collect(Collectors.toMap(CashierReconciliation::getCashierUserId,
                        Function.identity(), (first, ignored) -> first));

        long unsubmitted = activeCashiers.stream()
                .filter(cashier -> !byCashier.containsKey(cashier.getId()))
                .count();
        long reopened = activeCashiers.stream()
                .map(cashier -> byCashier.get(cashier.getId()))
                .filter(Objects::nonNull)
                .filter(value -> "REOPENED".equalsIgnoreCase(value.getLifecycleStatus()))
                .count();
        long unresolved = activeCashiers.stream()
                .map(cashier -> byCashier.get(cashier.getId()))
                .filter(Objects::nonNull)
                .filter(value -> !"SUBMITTED".equalsIgnoreCase(value.getLifecycleStatus())
                        && !"REOPENED".equalsIgnoreCase(value.getLifecycleStatus()))
                .count();

        if (unsubmitted > 0) {
            errors.add(unsubmitted + " active cashier(s) have not submitted reconciliation");
        }
        if (reopened > 0) {
            errors.add(reopened + " cashier reconciliation(s) remain REOPENED");
        }
        if (unresolved > 0) {
            errors.add(unresolved + " cashier reconciliation(s) remain unresolved");
        }
    }

    private void validateLegacyNonCashierBuckets(LocalDate businessDate, List<String> errors) {
        List<ChipBuyIn> buyIns = buyInRepository.findByBusinessDate(businessDate).stream()
                .filter(value -> isCash(value.getPaymentMode())).toList();
        List<ChipCashOut> cashOuts = cashOutRepository.findByBusinessDate(businessDate).stream()
                .filter(value -> isCash(value.getPaymentMode())).toList();
        List<LosingReturn> losingReturns = losingReturnRepository.findByBusinessDate(businessDate)
                .stream().filter(value -> isCash(value.getPaymentMode())).toList();

        Set<UUID> actorIds = new HashSet<>();
        buyIns.stream().map(ChipBuyIn::getCreatedBy).filter(Objects::nonNull).forEach(actorIds::add);
        cashOuts.stream().map(ChipCashOut::getCreatedBy).filter(Objects::nonNull).forEach(actorIds::add);
        losingReturns.stream().map(LosingReturn::getCreatedBy).filter(Objects::nonNull).forEach(actorIds::add);

        long unresolved = actorIds.stream()
                .filter(actorId -> userRepository.findById(actorId)
                        .flatMap(user -> Role.fromValue(user.getRole()))
                        .map(role -> role != Role.CASHIER).orElse(true))
                .filter(actorId -> !hasAuthoritativeNormalChain(actorId, businessDate))
                .filter(actorId -> !hasMatchingLegacyResolution(
                        actorId, businessDate, buyIns, cashOuts, losingReturns))
                .count();

        if (unresolved > 0) {
            errors.add(unresolved + " non-CASHIER CASH activity bucket(s) lack authoritative "
                    + "Opening Cash/reconciliation provenance and legacy resolution");
        }
    }

    private boolean hasAuthoritativeNormalChain(UUID actorId, LocalDate businessDate) {
        if (openingBalanceRepository.findByCashierUserIdAndBusinessDate(actorId, businessDate).isEmpty()) {
            return false;
        }
        return reconciliationRepository.findByCashierUserIdAndBusinessDate(actorId, businessDate)
                .map(value -> "SUBMITTED".equalsIgnoreCase(value.getLifecycleStatus()))
                .orElse(false);
    }

    private boolean hasMatchingLegacyResolution(UUID actorId, LocalDate businessDate,
            List<ChipBuyIn> buyIns, List<ChipCashOut> cashOuts,
            List<LosingReturn> losingReturns) {
        var existing = legacyResolutionRepository.findByActorUserIdAndBusinessDate(
                actorId, businessDate);
        if (existing.isEmpty()) return false;

        BigDecimal received = buyIns.stream()
                .filter(value -> actorId.equals(value.getCreatedBy()))
                .map(ChipBuyIn::getAmountReceived).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paid = cashOuts.stream()
                .filter(value -> actorId.equals(value.getCreatedBy()))
                .map(ChipCashOut::getCashPaid).reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(losingReturns.stream()
                        .filter(value -> actorId.equals(value.getCreatedBy()))
                        .map(LosingReturn::getAmountPaid).reduce(BigDecimal.ZERO, BigDecimal::add));
        LegacyCashActorResolution resolution = existing.get();
        return resolution.getCashReceived().compareTo(received) == 0
                && resolution.getCashPaid().compareTo(paid) == 0
                && resolution.getNetCashMovement().compareTo(received.subtract(paid)) == 0;
    }

    private boolean isCash(String paymentMode) {
        return "CASH".equalsIgnoreCase(paymentMode == null ? "" : paymentMode.trim());
    }
}
