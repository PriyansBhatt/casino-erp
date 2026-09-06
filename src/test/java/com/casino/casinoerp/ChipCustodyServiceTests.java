package com.casino.casinoerp;

import com.casino.casinoerp.dto.ChipCustodyTransferRequest;
import com.casino.casinoerp.dto.LegacySessionCustodyCorrectionRequest;
import com.casino.casinoerp.dto.SessionFinancialPositionResponse;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.exception.ResourceConflictException;
import com.casino.casinoerp.repository.*;
import com.casino.casinoerp.security.Role;
import com.casino.casinoerp.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChipCustodyServiceTests {
    private final ChipCustodyMovementRepository movements = mock(ChipCustodyMovementRepository.class);
    private final ChipCustodyInventoryRepository inventory = mock(ChipCustodyInventoryRepository.class);
    private final PitTableRepository pitTables = mock(PitTableRepository.class);
    private final CustomerSessionRepository customerSessions = mock(CustomerSessionRepository.class);
    private final PitTableCustomerAssignmentRepository assignments = mock(PitTableCustomerAssignmentRepository.class);
    private final BusinessDateService businessDates = mock(BusinessDateService.class);
    private final SystemLockService systemLock = mock(SystemLockService.class);
    private final AuthenticatedUserService authenticatedUsers = mock(AuthenticatedUserService.class);
    private final CurrentUserRoleService currentRoles = mock(CurrentUserRoleService.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final SessionFinancialPositionService financialPositions = mock(SessionFinancialPositionService.class);
    private final ChipCustodyService service = new ChipCustodyService(movements, inventory, pitTables,
            customerSessions, assignments,
            businessDates, systemLock, authenticatedUsers, currentRoles, new RolePermissionService(), audit,
            financialPositions);

    private final UUID actorId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID tableId = UUID.randomUUID();
    private final LocalDate businessDate = LocalDate.of(2026, 8, 8);
    private final Map<String, ChipCustodyInventory> rows = new HashMap<>();

    @BeforeEach
    void setUp() {
        when(businessDates.getCurrentBusinessDate()).thenReturn(businessDate);
        User actor = new User(); actor.setId(actorId);
        when(authenticatedUsers.getRequiredUser()).thenReturn(actor);
        when(currentRoles.getCurrentRole()).thenReturn(Optional.of(Role.SUPER_ADMIN));
        when(currentRoles.getCurrentUserRole()).thenReturn("SUPER_ADMIN");
        when(movements.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(movements.save(any())).thenAnswer(invocation -> {
            ChipCustodyMovement value = invocation.getArgument(0);
            value.setId(UUID.randomUUID());
            return value;
        });
        when(inventory.findForUpdate(anyString(), anyInt())).thenAnswer(invocation ->
                Optional.ofNullable(rows.get(key(invocation.getArgument(0), invocation.getArgument(1)))));
        when(inventory.findByLocationKeyOrderByDenomination(anyString())).thenAnswer(invocation -> {
            String location = invocation.getArgument(0);
            return rows.values().stream()
                    .filter(value -> location.equals(value.getLocationKey()))
                    .sorted(Comparator.comparing(ChipCustodyInventory::getDenomination))
                    .toList();
        });
        when(inventory.saveAll(any())).thenAnswer(invocation -> {
            Iterable<ChipCustodyInventory> values = invocation.getArgument(0);
            List<ChipCustodyInventory> saved = new ArrayList<>();
            values.forEach(value -> {
                rows.put(key(value.getLocationKey(), value.getDenomination()), value);
                saved.add(value);
            });
            return saved;
        });
        when(pitTables.findByIdForUpdate(tableId)).thenReturn(Optional.of(openTable()));
        when(customerSessions.findByIdForUpdate(sessionId)).thenReturn(Optional.of(openSession()));
        when(assignments.findActiveForUpdate(sessionId, tableId)).thenReturn(Optional.of(activeAssignment()));
        when(financialPositions.getPosition(sessionId)).thenReturn(position("5000"));
    }

    @Test
    void cageOpeningIsPersistedAsAuthoritativeInboundMovement() {
        seedCageRows(0);
        var response = service.initializeCage(request(Map.of(500, 20L, 1000, 10L), "opening"));

        assertThat(response.movementType()).isEqualTo(ChipCustodyMovementType.CAGE_OPENING);
        assertThat(response.totalValue()).isEqualByComparingTo("20000");
        assertThat(rows.get(key("CAGE", 500)).getQuantity()).isEqualTo(20);
        assertThat(rows.get(key("CAGE", 1000)).getQuantity()).isEqualTo(10);
    }

    @Test
    void cageInventoryReportsWhetherOpeningMovementExists() {
        when(movements.existsByMovementType(ChipCustodyMovementType.CAGE_OPENING))
                .thenReturn(false, true);

        assertThat(service.cageInventory().initialized()).isFalse();
        assertThat(service.cageInventory().initialized()).isTrue();
    }

    @Test
    void buyInDecreasesCageAndIncreasesCustomerSessionCustody() {
        seedCage(1000, 10);
        ChipCustodyMovement movement = service.recordBuyIn(UUID.randomUUID(), sessionId, businessDate,
                Map.of(1000, 4L), new BigDecimal("4000"), actorId);

        assertThat(movement.getMovementType()).isEqualTo(ChipCustodyMovementType.BUY_IN_ISSUE);
        assertThat(rows.get(key("CAGE", 1000)).getQuantity()).isEqualTo(6);
        assertThat(rows.get(key("CUSTOMER_SESSION:" + sessionId, 1000)).getQuantity()).isEqualTo(4);
    }

    @Test
    void insufficientCageDenominationRejectsWithoutMovementOrBalanceChange() {
        seedCage(1000, 1);
        assertThatThrownBy(() -> service.recordBuyIn(UUID.randomUUID(), sessionId, businessDate,
                Map.of(1000, 2L), new BigDecimal("2000"), actorId))
                .isInstanceOf(ResourceConflictException.class).hasMessageContaining("Insufficient physical chips");
        assertThat(rows.get(key("CAGE", 1000)).getQuantity()).isEqualTo(1);
        verify(movements, never()).save(any());
    }

    @Test
    void cashOutMovesCustomerChipsBackToCage() {
        seedCage(500, 3);
        seed("CUSTOMER_SESSION:" + sessionId, ChipCustodyLocationType.CUSTOMER_SESSION, sessionId, 500, 5);
        service.recordCashOut(UUID.randomUUID(), sessionId, businessDate,
                Map.of(500, 4L), new BigDecimal("2000"), actorId);

        assertThat(rows.get(key("CAGE", 500)).getQuantity()).isEqualTo(7);
        assertThat(rows.get(key("CUSTOMER_SESSION:" + sessionId, 500)).getQuantity()).isEqualTo(1);
    }

    @Test
    void customerCannotReturnMoreThanDenominationCustody() {
        seedCage(500, 0);
        seed("CUSTOMER_SESSION:" + sessionId, ChipCustodyLocationType.CUSTOMER_SESSION, sessionId, 500, 1);
        assertThatThrownBy(() -> service.recordCashOut(UUID.randomUUID(), sessionId, businessDate,
                Map.of(500, 2L), new BigDecimal("1000"), actorId))
                .isInstanceOf(ResourceConflictException.class);
        verify(movements, never()).save(any());
    }

    @Test
    void tableFloatIssueAndReturnMoveExactDenominations() {
        seedCage(5000, 10);
        service.issueTableFloat(tableId, request(Map.of(5000, 4L), "issue"));
        assertThat(rows.get(key("CAGE", 5000)).getQuantity()).isEqualTo(6);
        assertThat(rows.get(key("PIT_TABLE:" + tableId, 5000)).getQuantity()).isEqualTo(4);

        service.returnTableFloat(tableId, request(Map.of(5000, 3L), "return"));
        assertThat(rows.get(key("CAGE", 5000)).getQuantity()).isEqualTo(9);
        assertThat(rows.get(key("PIT_TABLE:" + tableId, 5000)).getQuantity()).isEqualTo(1);
    }

    @Test
    void tableCannotReturnMoreThanItsPhysicalInventory() {
        seedCage(10000, 0);
        seed("PIT_TABLE:" + tableId, ChipCustodyLocationType.PIT_TABLE, tableId, 10000, 1);
        assertThatThrownBy(() -> service.returnTableFloat(tableId,
                request(Map.of(10000, 2L), "too-much"))).isInstanceOf(ResourceConflictException.class);
    }

    @Test
    void customerToTableAndReturnMoveExactDenominationsWithoutChangingFinancialData() {
        seed("CUSTOMER_SESSION:" + sessionId, ChipCustodyLocationType.CUSTOMER_SESSION,
                sessionId, 5000, 5);

        var issued = service.moveCustomerChipsToTable(tableId, sessionId,
                request(Map.of(5000, 4L), "customer-table"));
        assertThat(issued.movementType()).isEqualTo(ChipCustodyMovementType.CUSTOMER_TO_TABLE);
        assertThat(issued.customerSessionId()).isEqualTo(sessionId);
        assertThat(issued.pitTableId()).isEqualTo(tableId);
        assertThat(rows.get(key("CUSTOMER_SESSION:" + sessionId, 5000)).getQuantity()).isEqualTo(1);
        assertThat(rows.get(key("PIT_TABLE:" + tableId, 5000)).getQuantity()).isEqualTo(4);

        var returned = service.returnTableChipsToCustomer(tableId, sessionId,
                request(Map.of(5000, 2L), "table-customer"));
        assertThat(returned.movementType()).isEqualTo(ChipCustodyMovementType.TABLE_TO_CUSTOMER);
        assertThat(rows.get(key("CUSTOMER_SESSION:" + sessionId, 5000)).getQuantity()).isEqualTo(3);
        assertThat(rows.get(key("PIT_TABLE:" + tableId, 5000)).getQuantity()).isEqualTo(2);
    }

    @Test
    void customerTableTransfersRejectInsufficientInventoryAndInvalidAssignmentLifecycle() {
        seed("CUSTOMER_SESSION:" + sessionId, ChipCustodyLocationType.CUSTOMER_SESSION,
                sessionId, 5000, 1);
        assertThatThrownBy(() -> service.moveCustomerChipsToTable(tableId, sessionId,
                request(Map.of(5000, 2L), "too-many")))
                .isInstanceOf(ResourceConflictException.class).hasMessageContaining("Insufficient");

        when(assignments.findActiveForUpdate(sessionId, tableId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.moveCustomerChipsToTable(tableId, sessionId,
                request(Map.of(5000, 1L), "not-assigned"))).hasMessageContaining("actively assigned");

        when(customerSessions.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session("CLOSED", businessDate)));
        assertThatThrownBy(() -> service.moveCustomerChipsToTable(tableId, sessionId,
                request(Map.of(5000, 1L), "closed-session"))).hasMessageContaining("must be OPEN");
    }

    @Test
    void customerTableTransfersRejectWrongDateClosedTableAndSystemLock() {
        when(customerSessions.findByIdForUpdate(sessionId))
                .thenReturn(Optional.of(session("OPEN", businessDate.minusDays(1))));
        assertThatThrownBy(() -> service.moveCustomerChipsToTable(tableId, sessionId,
                request(Map.of(500, 1L), "wrong-date"))).hasMessageContaining("current OPEN Business Date");

        when(customerSessions.findByIdForUpdate(sessionId)).thenReturn(Optional.of(openSession()));
        PitTable closed = openTable(); closed.setStatus("CLOSED");
        when(pitTables.findByIdForUpdate(tableId)).thenReturn(Optional.of(closed));
        assertThatThrownBy(() -> service.moveCustomerChipsToTable(tableId, sessionId,
                request(Map.of(500, 1L), "closed-table"))).hasMessageContaining("must be OPEN");

        when(systemLock.isSystemLocked()).thenReturn(true);
        assertThatThrownBy(() -> service.moveCustomerChipsToTable(tableId, sessionId,
                request(Map.of(500, 1L), "locked"))).hasMessageContaining("System is locked");
    }

    @Test
    void validatesDenominationsTotalsOverflowAndIdempotency() {
        seedCage(500, 10);
        assertThatThrownBy(() -> service.recordBuyIn(UUID.randomUUID(), sessionId, businessDate,
                Map.of(2000, 1L), new BigDecimal("2000"), actorId)).hasMessageContaining("Unsupported");
        assertThatThrownBy(() -> service.recordBuyIn(UUID.randomUUID(), sessionId, businessDate,
                Map.of(500, -1L), new BigDecimal("500"), actorId)).hasMessageContaining("non-negative");
        assertThatThrownBy(() -> service.recordBuyIn(UUID.randomUUID(), sessionId, businessDate,
                Map.of(500, 1L), new BigDecimal("499"), actorId)).hasMessageContaining("does not match");
        assertThatThrownBy(() -> service.recordBuyIn(UUID.randomUUID(), sessionId, businessDate,
                Map.of(25000, Long.MAX_VALUE), BigDecimal.ONE, actorId)).hasMessageContaining("exceeds");

        ChipCustodyMovement replay = movement(ChipCustodyMovementType.BUY_IN_ISSUE,
                null, sessionId, Map.of(500, 1L), "500");
        when(movements.findByIdempotencyKey("BUY_IN_ISSUE:" + replay.getRelatedTransactionId()))
                .thenReturn(Optional.of(replay));
        ChipCustodyMovement result = service.recordBuyIn(replay.getRelatedTransactionId(), sessionId,
                businessDate, Map.of(500, 1L), new BigDecimal("500"), actorId);
        assertThat(result).isSameAs(replay);
    }

    @Test
    void superAdminCorrectsPositiveLegacyGapWithoutChangingFinancialPosition() {
        seedCage(5000, 3);

        var result = service.correctLegacySessionCustody(correction(Map.of(5000, 1L)));

        assertThat(result.movementType()).isEqualTo(ChipCustodyMovementType.LEGACY_CUSTODY_CORRECTION);
        assertThat(result.sourceType()).isEqualTo(ChipCustodyLocationType.CAGE);
        assertThat(result.destinationType()).isEqualTo(ChipCustodyLocationType.CUSTOMER_SESSION);
        assertThat(result.customerSessionId()).isEqualTo(sessionId);
        assertThat(result.totalValue()).isEqualByComparingTo("5000");
        assertThat(result.correctionReason()).isEqualTo("Legacy pre-ledger custody correction");
        assertThat(rows.get(key("CAGE", 5000)).getQuantity()).isEqualTo(2);
        assertThat(rows.get(key("CUSTOMER_SESSION:" + sessionId, 5000)).getQuantity()).isEqualTo(1);
        verify(financialPositions, times(2)).getPosition(sessionId);
        verify(audit).log(eq("LEGACY_SESSION_CUSTODY_CORRECTION"), eq("CHIP_CUSTODY_MOVEMENT"),
                any(), eq(actorId), contains("resultingCustodyTotal=5000"));
    }

    @Test
    void rejectsNonPositiveExcessiveAndAlreadyResolvedCorrections() {
        seedCage(5000, 5);
        when(financialPositions.getPosition(sessionId)).thenReturn(position("0"));
        assertThatThrownBy(() -> service.correctLegacySessionCustody(correction(Map.of(5000, 1L))))
                .hasMessageContaining("positive authoritative financial position");
        when(financialPositions.getPosition(sessionId)).thenReturn(position("-500"));
        assertThatThrownBy(() -> service.correctLegacySessionCustody(correction(Map.of(5000, 1L))))
                .hasMessageContaining("positive authoritative financial position");

        when(financialPositions.getPosition(sessionId)).thenReturn(position("5000"));
        assertThatThrownBy(() -> service.correctLegacySessionCustody(correction(Map.of(5000, 2L))))
                .hasMessageContaining("cannot exceed");

        seed("CUSTOMER_SESSION:" + sessionId, ChipCustodyLocationType.CUSTOMER_SESSION,
                sessionId, 5000, 1);
        assertThatThrownBy(() -> service.correctLegacySessionCustody(correction(Map.of(5000, 1L))))
                .hasMessageContaining("already resolves");
        verify(movements, never()).save(any());
    }

    @Test
    void idempotentCorrectionRetryReturnsOriginalMovementWithoutApplyingCustodyAgain() {
        ChipCustodyMovement replay = movement(ChipCustodyMovementType.LEGACY_CUSTODY_CORRECTION,
                null, sessionId, Map.of(5000, 1L), "5000");
        replay.setId(UUID.randomUUID());
        replay.setCorrectionReason("Legacy pre-ledger custody correction");
        when(movements.findByIdempotencyKey("legacy-correction-1")).thenReturn(Optional.of(replay));

        var result = service.correctLegacySessionCustody(correction(Map.of(5000, 1L)));

        assertThat(result.id()).isEqualTo(replay.getId());
        verify(financialPositions, never()).getPosition(any());
        verify(inventory, never()).saveAll(any());
    }

    @Test
    void directServiceUseRejectsWhitespacePaddedShortReason() {
        var request = new LegacySessionCustodyCorrectionRequest(sessionId, Map.of(5000, 1L),
                "         x", "legacy-short-reason");
        assertThatThrownBy(() -> service.correctLegacySessionCustody(request))
                .hasMessageContaining("between 10 and 500");
    }

    @Test
    void rejectsInsufficientCageClosedLifecycleSystemLockAndUnauthorizedRoles() {
        seedCage(5000, 0);
        assertThatThrownBy(() -> service.correctLegacySessionCustody(correction(Map.of(5000, 1L))))
                .hasMessageContaining("Insufficient physical chips");

        seedCage(5000, 1);
        when(customerSessions.findByIdForUpdate(sessionId))
                .thenReturn(Optional.of(session("CLOSED", businessDate)));
        assertThatThrownBy(() -> service.correctLegacySessionCustody(correction(Map.of(5000, 1L))))
                .hasMessageContaining("must be OPEN");

        when(customerSessions.findByIdForUpdate(sessionId)).thenReturn(Optional.of(openSession()));
        doThrow(new RuntimeException("Current business date is not OPEN."))
                .when(businessDates).validateBusinessDateIsOpen();
        assertThatThrownBy(() -> service.correctLegacySessionCustody(correction(Map.of(5000, 1L))))
                .hasMessageContaining("not OPEN");
        reset(businessDates);
        when(businessDates.getCurrentBusinessDate()).thenReturn(businessDate);

        when(systemLock.isSystemLocked()).thenReturn(true);
        assertThatThrownBy(() -> service.correctLegacySessionCustody(correction(Map.of(5000, 1L))))
                .hasMessageContaining("System is locked");
        when(systemLock.isSystemLocked()).thenReturn(false);

        when(currentRoles.getCurrentRole()).thenReturn(Optional.of(Role.DIRECTOR), Optional.of(Role.CASHIER));
        assertThatThrownBy(() -> service.correctLegacySessionCustody(correction(Map.of(5000, 1L))))
                .hasMessageContaining("Only Super Admin");
        assertThatThrownBy(() -> service.correctLegacySessionCustody(correction(Map.of(5000, 1L))))
                .hasMessageContaining("Only Super Admin");
    }

    private LegacySessionCustodyCorrectionRequest correction(Map<Integer, Long> denominations) {
        return new LegacySessionCustodyCorrectionRequest(sessionId, denominations,
                "Legacy pre-ledger custody correction", "legacy-correction-1");
    }

    private SessionFinancialPositionResponse position(String calculatedPosition) {
        return new SessionFinancialPositionResponse(UUID.randomUUID(), sessionId, businessDate,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal(calculatedPosition));
    }

    private ChipCustodyTransferRequest request(Map<Integer, Long> denominations, String key) {
        return new ChipCustodyTransferRequest(denominations, key);
    }

    private void seedCageRows(long quantity) {
        ChipDenomination.supportedValues().forEach(value -> seedCage(value, quantity));
    }

    private void seedCage(int denomination, long quantity) {
        seed("CAGE", ChipCustodyLocationType.CAGE, null, denomination, quantity);
    }

    private void seed(String locationKey, ChipCustodyLocationType type, UUID referenceId,
            int denomination, long quantity) {
        ChipCustodyInventory value = new ChipCustodyInventory();
        value.setLocationKey(locationKey); value.setLocationType(type); value.setReferenceId(referenceId);
        value.setDenomination(denomination); value.setQuantity(quantity);
        rows.put(key(locationKey, denomination), value);
    }

    private String key(String location, Integer denomination) {
        return location + ":" + denomination;
    }

    private PitTable openTable() {
        PitTable table = new PitTable(); table.setId(tableId); table.setStatus("OPEN");
        table.setBusinessDate(businessDate); table.setOpeningFloat(new BigDecimal("20000")); return table;
    }

    private CustomerSession openSession() { return session("OPEN", businessDate); }
    private CustomerSession session(String status, LocalDate date) {
        CustomerSession value = new CustomerSession(); value.setId(sessionId);
        value.setStatus(status); value.setBusinessDate(date); return value;
    }
    private PitTableCustomerAssignment activeAssignment() {
        PitTableCustomerAssignment value = new PitTableCustomerAssignment(); value.setId(UUID.randomUUID());
        value.setCustomerSessionId(sessionId); value.setPitTableId(tableId);
        value.setBusinessDate(businessDate); value.setStatus(PitTableCustomerAssignmentStatus.ACTIVE);
        return value;
    }

    private ChipCustodyMovement movement(ChipCustodyMovementType type, UUID source, UUID destination,
            Map<Integer, Long> denominations, String total) {
        ChipCustodyMovement value = new ChipCustodyMovement();
        value.setMovementType(type); value.setSourceReferenceId(source); value.setDestinationReferenceId(destination);
        value.setDenominations(new LinkedHashMap<>(denominations)); value.setTotalValue(new BigDecimal(total));
        value.setRelatedTransactionId(UUID.randomUUID()); return value;
    }
}
