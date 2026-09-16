package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.SessionFinancialPositionResponse;
import com.casino.casinoerp.entity.CustomerSession;
import com.casino.casinoerp.entity.PitTable;
import com.casino.casinoerp.entity.PitTableCustomerAssignmentStatus;
import com.casino.casinoerp.repository.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class BusinessDateValidationService {

    private final CustomerSessionRepository sessionRepository;
    private final PitTableCustomerAssignmentRepository assignmentRepository;
    private final SessionFinancialPositionService financialPositionService;
    private final PitTableRepository pitTableRepository;
    private final PitTableStaffAssignmentRepository staffAssignmentRepository;
    private final ChipCustodyInventoryRepository custodyInventoryRepository;
    private final com.casino.casinoerp.repository.BusinessDateCloseReadRepository closeReads;

    public BusinessDateValidationService(
            CustomerSessionRepository sessionRepository,
            PitTableCustomerAssignmentRepository assignmentRepository,
            SessionFinancialPositionService financialPositionService,
            PitTableRepository pitTableRepository,
            PitTableStaffAssignmentRepository staffAssignmentRepository,
            ChipCustodyInventoryRepository custodyInventoryRepository,
            com.casino.casinoerp.repository.BusinessDateCloseReadRepository closeReads) {
        this.sessionRepository = sessionRepository;
        this.assignmentRepository = assignmentRepository;
        this.financialPositionService = financialPositionService;
        this.pitTableRepository = pitTableRepository;
        this.staffAssignmentRepository = staffAssignmentRepository;
        this.custodyInventoryRepository = custodyInventoryRepository;
        this.closeReads = closeReads;
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
        long missing = 0, reopened = 0, unresolved = 0;
        for (var actor : closeReads.actors(businessDate)) {
            String status = actor.reconciliationStatus();
            if ("REOPENED".equalsIgnoreCase(status)) {
                reopened++;
            } else if ("SUBMITTED".equalsIgnoreCase(status) && actor.hasOpening()) {
                // Complete normal opening/reconciliation chain, regardless of account login eligibility.
            } else if (status == null && !actor.hasOpening() && actor.legacyResolved()) {
                // Preserve an existing, amount-matching legacy resolution; never bypass a reopened chain.
            } else if (status == null) {
                missing++;
            } else {
                unresolved++;
            }
        }
        if (missing > 0) errors.add(missing
                + " cash activity/opening-balance actor(s) have not submitted reconciliation or resolved the existing legacy cash bucket");
        if (reopened > 0) errors.add(reopened + " cashier reconciliation(s) remain REOPENED");
        if (unresolved > 0) errors.add(unresolved
                + " cash actor(s) lack a complete Opening Cash/SUBMITTED reconciliation chain");
    }
}
