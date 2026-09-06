package com.casino.casinoerp;

import com.casino.casinoerp.dto.PitTableModeResponse;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.repository.*;
import com.casino.casinoerp.service.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.springframework.security.access.AccessDeniedException;

class PitTableModeServiceTests {
    private final PitTableRepository tables = mock(PitTableRepository.class);
    private final PitTableAccessService access = mock(PitTableAccessService.class);
    private final PitTableStaffAssignmentRepository staff = mock(PitTableStaffAssignmentRepository.class);
    private final PitTableCustomerAssignmentRepository assignments = mock(PitTableCustomerAssignmentRepository.class);
    private final VerifiedGamingResultRepository results = mock(VerifiedGamingResultRepository.class);
    private final ChipCustodyInventoryRepository custody = mock(ChipCustodyInventoryRepository.class);
    private final ChipCustodyService custodyService = mock(ChipCustodyService.class);
    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final BusinessDateService businessDates = mock(BusinessDateService.class);
    private final SystemLockService systemLock = mock(SystemLockService.class);
    private final PitTableModeService service = new PitTableModeService(tables, access, staff, assignments,
            results, custody, custodyService, customers, businessDates, systemLock);

    @Test
    void snapshotUsesOneBatchCallPerGrowingPlayerDataset() {
        UUID tableId = UUID.randomUUID();
        UUID session1 = UUID.randomUUID();
        UUID session2 = UUID.randomUUID();
        PitTable table = table(tableId);
        when(tables.findById(tableId)).thenReturn(Optional.of(table));
        when(staff.findActiveStaffForOverview(List.of(tableId))).thenReturn(List.of());
        PitTableModePlayerProjection player1 = player(session1);
        PitTableModePlayerProjection player2 = player(session2);
        when(assignments.findActiveModePlayers(tableId)).thenReturn(List.of(player1, player2));
        when(results.summarizeByTable(tableId)).thenReturn(List.of());
        when(custody.summarizeCustomerSessions(List.of(session1, session2))).thenReturn(List.of());
        when(custodyService.tableInventory(tableId)).thenReturn(new com.casino.casinoerp.dto.ChipCustodyInventoryResponse(
                ChipCustodyLocationType.PIT_TABLE, tableId, true, Map.of(5000, 2L), new BigDecimal("10000")));
        BusinessDate open = new BusinessDate(); open.setBusinessDate(table.getBusinessDate());
        when(businessDates.getCurrentOpenBusinessDate()).thenReturn(Optional.of(open));

        PitTableModeResponse response = service.snapshot(tableId);

        assertThat(response.players()).hasSize(2);
        assertThat(response.tableCustody().totalValue()).isEqualByComparingTo("10000");
        assertThat(response.supportedDenominations()).containsExactly(500, 1000, 5000, 10000, 25000);
        verify(assignments, times(1)).findActiveModePlayers(tableId);
        verify(results, times(1)).summarizeByTable(tableId);
        verify(custody, times(1)).summarizeCustomerSessions(List.of(session1, session2));
    }

    @Test
    void noPlayersAvoidsEmptyBatchCustodyQuery() {
        UUID tableId = UUID.randomUUID();
        when(tables.findById(tableId)).thenReturn(Optional.of(table(tableId)));
        when(staff.findActiveStaffForOverview(List.of(tableId))).thenReturn(List.of());
        when(assignments.findActiveModePlayers(tableId)).thenReturn(List.of());
        when(results.summarizeByTable(tableId)).thenReturn(List.of());
        when(custodyService.tableInventory(tableId)).thenReturn(new com.casino.casinoerp.dto.ChipCustodyInventoryResponse(
                ChipCustodyLocationType.PIT_TABLE, tableId, false, Map.of(), BigDecimal.ZERO));
        when(businessDates.getCurrentOpenBusinessDate()).thenReturn(Optional.empty());

        assertThat(service.snapshot(tableId).players()).isEmpty();
        verify(custody, never()).summarizeCustomerSessions(any());
    }

    @Test
    void exactOperationAccessIsRequiredBeforeSnapshotQueries() {
        UUID tableId = UUID.randomUUID();
        when(tables.findById(tableId)).thenReturn(Optional.of(table(tableId)));
        doThrow(new AccessDeniedException("assignment required")).when(access).requireOperationalAccess(tableId);

        assertThatThrownBy(() -> service.snapshot(tableId)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(staff, assignments, results, custody, custodyService);
    }

    private PitTable table(UUID id) {
        PitTable table = new PitTable(); table.setId(id); table.setPhysicalTableId(UUID.randomUUID());
        table.setTableCode("BAC-001"); table.setTableName("Baccarat 1"); table.setGameType("BACCARAT");
        table.setBusinessDate(LocalDate.of(2026, 9, 2)); table.setStatus("OPEN");
        table.setOpeningFloat(new BigDecimal("10000")); table.setMaxPlayers(7); return table;
    }

    private PitTableModePlayerProjection player(UUID sessionId) {
        PitTableModePlayerProjection value = mock(PitTableModePlayerProjection.class);
        when(value.getAssignmentId()).thenReturn(UUID.randomUUID()); when(value.getCustomerId()).thenReturn(UUID.randomUUID());
        when(value.getCustomerCode()).thenReturn("CUS-1001"); when(value.getCustomerName()).thenReturn("Test Customer");
        when(value.getCustomerSessionId()).thenReturn(sessionId); when(value.getSessionCode()).thenReturn("SES-1");
        when(value.getJoinedAt()).thenReturn(LocalDateTime.of(2026, 9, 2, 13, 0)); return value;
    }
}
