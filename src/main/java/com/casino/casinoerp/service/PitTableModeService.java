package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.exception.ResourceNotFoundException;
import com.casino.casinoerp.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PitTableModeService {
    private static final int MINIMUM_SEARCH_LENGTH = 2;

    private final PitTableRepository tables;
    private final PitTableAccessService access;
    private final PitTableStaffAssignmentRepository staff;
    private final PitTableCustomerAssignmentRepository assignments;
    private final VerifiedGamingResultRepository results;
    private final ChipCustodyInventoryRepository custody;
    private final ChipCustodyService custodyService;
    private final CustomerRepository customers;
    private final BusinessDateService businessDates;
    private final SystemLockService systemLock;

    public PitTableModeService(PitTableRepository tables, PitTableAccessService access,
            PitTableStaffAssignmentRepository staff, PitTableCustomerAssignmentRepository assignments,
            VerifiedGamingResultRepository results, ChipCustodyInventoryRepository custody,
            ChipCustodyService custodyService, CustomerRepository customers,
            BusinessDateService businessDates, SystemLockService systemLock) {
        this.tables = tables;
        this.access = access;
        this.staff = staff;
        this.assignments = assignments;
        this.results = results;
        this.custody = custody;
        this.custodyService = custodyService;
        this.customers = customers;
        this.businessDates = businessDates;
        this.systemLock = systemLock;
    }

    @Transactional(readOnly = true)
    public PitTableModeResponse snapshot(UUID tableId) {
        PitTable table = requiredTable(tableId);
        access.requireOperationalAccess(tableId);

        List<PitTableActiveStaffProjection> activeStaff = staff.findActiveStaffForOverview(List.of(tableId));
        List<PitTableModePlayerProjection> activePlayers = assignments.findActiveModePlayers(tableId);
        Map<UUID, PitTablePlayerResultSummaryProjection> resultTotals = results.summarizeByTable(tableId).stream()
                .collect(Collectors.toMap(PitTablePlayerResultSummaryProjection::getAssignmentId, Function.identity()));
        List<UUID> sessionIds = activePlayers.stream().map(PitTableModePlayerProjection::getCustomerSessionId).toList();
        Map<UUID, BigDecimal> custodyTotals = sessionIds.isEmpty() ? Map.of()
                : custody.summarizeCustomerSessions(sessionIds).stream().collect(Collectors.toMap(
                        CustomerSessionCustodySummaryProjection::getCustomerSessionId,
                        CustomerSessionCustodySummaryProjection::getCustodyTotal));
        ChipCustodyInventoryResponse tableCustody = custodyService.tableInventory(tableId);
        var openBusinessDate = businessDates.getCurrentOpenBusinessDate();

        PitTableModeResponse.Staff dealer = staff(activeStaff, PitTableStaffAssignmentRole.DEALER);
        PitTableModeResponse.Staff supervisor = staff(activeStaff, PitTableStaffAssignmentRole.PIT_SUPERVISOR);
        List<PitTableModeResponse.Player> players = activePlayers.stream().map(player -> {
            PitTablePlayerResultSummaryProjection totals = resultTotals.get(player.getAssignmentId());
            BigDecimal wins = totals == null ? BigDecimal.ZERO : totals.getVerifiedWinTotal();
            BigDecimal losses = totals == null ? BigDecimal.ZERO : totals.getVerifiedLossTotal();
            boolean custodyInitialized = custodyTotals.containsKey(player.getCustomerSessionId());
            return new PitTableModeResponse.Player(player.getAssignmentId(), player.getCustomerId(),
                    player.getCustomerCode(), player.getCustomerName(), player.getCustomerSessionId(),
                    player.getSessionCode(), null, player.getJoinedAt(),
                    PitTableCustomerAssignmentStatus.ACTIVE, wins, losses, losses.subtract(wins),
                    custodyInitialized, custodyTotals.getOrDefault(player.getCustomerSessionId(), BigDecimal.ZERO));
        }).toList();

        return new PitTableModeResponse(table.getId(), table.getPhysicalTableId(), table.getTableCode(),
                table.getTableName(), table.getGameType(), table.getBusinessDate(), table.getStatus(),
                table.getOpeningFloat(), table.getMaxPlayers(), dealer, supervisor, players,
                new PitTableModeResponse.Custody(tableCustody.initialized(), tableCustody.denominations(),
                        tableCustody.totalValue()),
                systemLock.isSystemLocked(), openBusinessDate.isPresent(),
                openBusinessDate.map(BusinessDate::getBusinessDate).orElse(null),
                ChipDenomination.supportedValues().stream().sorted().toList(),
                resultTotals.values().stream().map(PitTablePlayerResultSummaryProjection::getVerifiedWinTotal).reduce(BigDecimal.ZERO, BigDecimal::add),
                resultTotals.values().stream().map(PitTablePlayerResultSummaryProjection::getVerifiedLossTotal).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Transactional(readOnly = true)
    public List<EligiblePitTablePlayerResponse> eligiblePlayers(UUID tableId, String query) {
        PitTable table = requiredTable(tableId);
        access.requireOperationalAccess(tableId);
        String normalized = query == null ? "" : query.trim();
        if (normalized.length() < MINIMUM_SEARCH_LENGTH) {
            throw new IllegalArgumentException("Eligible-player search requires at least 2 characters.");
        }
        var open = businessDates.getCurrentOpenBusinessDate();
        if (open.isEmpty()) return List.of();
        boolean tableOperational = "OPEN".equalsIgnoreCase(table.getStatus())
                && Objects.equals(table.getBusinessDate(), open.get().getBusinessDate());
        boolean locked = systemLock.isSystemLocked();
        return customers.searchOpenSessionCandidates(open.get().getBusinessDate(), normalized).stream()
                .map(candidate -> eligible(candidate, tableOperational, locked)).toList();
    }

    private EligiblePitTablePlayerResponse eligible(EligiblePitTablePlayerProjection candidate,
            boolean tableOperational, boolean locked) {
        CustomerStatus status = CustomerStatus.valueOf(candidate.getCustomerStatus().toUpperCase(Locale.ROOT));
        String reason = null;
        if (!tableOperational) reason = "Pit Table is not OPEN for the current Business Date.";
        else if (locked) reason = "System is locked. Pit table player operations are not allowed.";
        else if (status != CustomerStatus.ACTIVE) reason = "Customer is not ACTIVE.";
        else if (candidate.getActiveAssignmentId() != null) reason = "Customer session is already assigned to an active Pit Table.";
        return new EligiblePitTablePlayerResponse(candidate.getCustomerId(), candidate.getCustomerCode(),
                candidate.getCustomerName(), status, candidate.getCustomerSessionId(), candidate.getSessionCode(),
                null, reason == null, reason);
    }

    private PitTable requiredTable(UUID tableId) {
        return tables.findById(tableId)
                .orElseThrow(() -> new ResourceNotFoundException("Pit table not found."));
    }

    private PitTableModeResponse.Staff staff(List<PitTableActiveStaffProjection> rows,
            PitTableStaffAssignmentRole role) {
        return rows.stream().filter(row -> row.getAssignmentRole() == role).findFirst()
                .map(row -> new PitTableModeResponse.Staff(row.getAssignmentId(), row.getUserId(),
                        row.getUsername(), row.getDisplayName(), row.getStartedAt()))
                .orElse(null);
    }
}
