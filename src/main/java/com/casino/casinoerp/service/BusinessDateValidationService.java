package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.SessionFinancialPositionResponse;
import com.casino.casinoerp.entity.CashierReconciliation;
import com.casino.casinoerp.entity.ChipBuyIn;
import com.casino.casinoerp.entity.ChipCashOut;
import com.casino.casinoerp.entity.CustomerSession;
import com.casino.casinoerp.entity.LegacyCashActorResolution;
import com.casino.casinoerp.entity.LosingReturn;
import com.casino.casinoerp.entity.PitTableCustomerAssignmentStatus;
import com.casino.casinoerp.entity.PitTable;
import com.casino.casinoerp.entity.User;
import com.casino.casinoerp.repository.CashierReconciliationRepository;
import com.casino.casinoerp.repository.CashierOpeningBalanceRepository;
import com.casino.casinoerp.repository.ChipBuyInRepository;
import com.casino.casinoerp.repository.ChipCashOutRepository;
import com.casino.casinoerp.repository.CustomerSessionRepository;
import com.casino.casinoerp.repository.PitTableCustomerAssignmentRepository;
import com.casino.casinoerp.repository.PitTableRepository;
import com.casino.casinoerp.repository.PitTableStaffAssignmentRepository;
import com.casino.casinoerp.repository.ChipCustodyInventoryRepository;
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
    private final PitTableStaffAssignmentRepository staffAssignmentRepository;
    private final ChipCustodyInventoryRepository custodyInventoryRepository;
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
            PitTableStaffAssignmentRepository staffAssignmentRepository,
            ChipCustodyInventoryRepository custodyInventoryRepository,
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
        this.staffAssignmentRepository = staffAssignmentRepository;
        this.custodyInventoryRepository = custodyInventoryRepository;
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
        List<CustomerSession> sessions = sessionRepository
                .findByBusinessDateOrderByEntryTimeAsc(businessDate);
        List<CustomerSession> openSessions = sessions.stream()
                .filter(session -> "OPEN".equalsIgnoreCase(session.getStatus()))
                .toList();

        if (!openSessions.isEmpty()) {
            errors.add(openSessions.size() + " customer session(s) still OPEN");
        }

        long activeAssignments = assignmentRepository
                .findByBusinessDateAndStatusOrderByJoinedAtAsc(
                        businessDate, PitTableCustomerAssignmentStatus.ACTIVE).size();
        if (activeAssignments > 0) {
            errors.add(activeAssignments + " active customer Pit Table assignment(s) remain for the Business Date");
        }

        long activeStaffAssignments = staffAssignmentRepository
                .findByBusinessDateAndEndedAtIsNullOrderByStartedAtAsc(businessDate).size();
        if (activeStaffAssignments > 0) {
            errors.add(activeStaffAssignments + " active staff Pit Table assignment(s) remain for the Business Date");
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

        long closedPositivePositions = 0;
        long closedNegativePositions = 0;
        for (CustomerSession session : sessions) {
            if (!"CLOSED".equalsIgnoreCase(session.getStatus())) continue;
            int comparison = financialPositionService.getPosition(session.getId())
                    .calculatedChipPosition().compareTo(BigDecimal.ZERO);
            if (comparison > 0) closedPositivePositions++;
            if (comparison < 0) closedNegativePositions++;
        }
        if (closedPositivePositions > 0) {
            errors.add(closedPositivePositions
                    + " inconsistent CLOSED customer session(s) have unresolved positive chip positions");
        }
        if (closedNegativePositions > 0) {
            errors.add(closedNegativePositions
                    + " inconsistent CLOSED customer session(s) have unresolved negative chip positions");
        }

        List<PitTable> tables = pitTableRepository.findByBusinessDate(businessDate);
        long openTables = tables.stream().filter(table -> "OPEN".equalsIgnoreCase(table.getStatus())).count();
        if (openTables > 0) {
            errors.add(openTables + " Pit Table(s) still OPEN for the Business Date");
        }

        validateOperationalCustody(sessions, tables, errors);

        validateCashierReconciliations(businessDate, errors);
        validateLegacyNonCashierBuckets(businessDate, errors);
        return errors;
    }

    private void validateOperationalCustody(
            List<CustomerSession> sessions, List<PitTable> tables, List<String> errors) {
        List<UUID> sessionIds = sessions.stream().map(CustomerSession::getId).toList();
        long sessionCustody = sessionIds.isEmpty() ? 0 : custodyInventoryRepository
                .summarizeCustomerSessions(sessionIds).stream()
                .filter(summary -> summary.getCustodyTotal().compareTo(BigDecimal.ZERO) != 0)
                .count();
        if (sessionCustody > 0) {
            errors.add(sessionCustody
                    + " customer session(s) retain unresolved physical chip custody");
        }

        List<UUID> tableIds = tables.stream().map(PitTable::getId).toList();
        long tableCustody = tableIds.isEmpty() ? 0 : custodyInventoryRepository
                .summarizePitTables(tableIds).stream()
                .filter(summary -> summary.getCustodyTotal().compareTo(BigDecimal.ZERO) != 0)
                .count();
        if (tableCustody > 0) {
            errors.add(tableCustody + " Pit Table(s) retain unresolved physical chip custody");
        }
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
