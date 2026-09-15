package com.casino.casinoerp;

import com.casino.casinoerp.dto.CreatePitTableRequest;
import com.casino.casinoerp.entity.PitTable;
import com.casino.casinoerp.exception.ResourceConflictException;
import com.casino.casinoerp.repository.PitTableRepository;
import com.casino.casinoerp.security.Role;
import com.casino.casinoerp.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PitTableServiceTests {
    private final PitTableRepository repository = mock(PitTableRepository.class);
    private final BusinessDateService businessDateService = mock(BusinessDateService.class);
    private final SystemLockService systemLockService = mock(SystemLockService.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final CurrentUserRoleService currentUserRoleService = mock(CurrentUserRoleService.class);
    private final com.casino.casinoerp.repository.PitTableCustomerAssignmentRepository assignmentRepository =
            mock(com.casino.casinoerp.repository.PitTableCustomerAssignmentRepository.class);
    private final ChipCustodyService chipCustodyService = mock(ChipCustodyService.class);
    private final PitTableAccessService tableAccess = mock(PitTableAccessService.class);
    private final com.casino.casinoerp.repository.PitTableStaffAssignmentRepository staff = mock(com.casino.casinoerp.repository.PitTableStaffAssignmentRepository.class);
    private final PitTableService service = new PitTableService(repository, businessDateService,
            systemLockService, auditLogService, currentUserRoleService, new RolePermissionService(),
            assignmentRepository, chipCustodyService, tableAccess, staff);
    private final LocalDate businessDate = LocalDate.of(2026, 8, 8);
    private final CreatePitTableRequest request = new CreatePitTableRequest(
            " t-bac-10 ", "Baccarat Table 10", "Baccarat", new BigDecimal("100000"), null);

    @BeforeEach void setUp() {
        when(currentUserRoleService.getCurrentUserRole()).thenReturn("PIT_SUPERVISOR");
        when(currentUserRoleService.getCurrentRole()).thenReturn(Optional.of(Role.PIT_SUPERVISOR));
        when(businessDateService.getCurrentBusinessDate()).thenReturn(businessDate);
        when(repository.findByTableCodeIgnoreCase("T-BAC-10")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(call -> {
            PitTable table = call.getArgument(0); table.setId(UUID.randomUUID()); return table;
        });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"DEALER", "PIT_SUPERVISOR"})
    void activeStaffBlocksCloseUntilExplicitlyEnded(String role) {
        PitTable table = new PitTable(); table.setId(UUID.randomUUID()); table.setStatus("OPEN"); table.setBusinessDate(businessDate);
        when(repository.findByIdForUpdate(table.getId())).thenReturn(Optional.of(table));
        when(staff.existsByPitTableIdAndEndedAtIsNull(table.getId())).thenReturn(true);
        assertThatThrownBy(() -> service.closeTable(table.getId(), BigDecimal.ZERO)).hasMessageContaining("End all active");
        verify(repository, never()).save(any());
        when(staff.existsByPitTableIdAndEndedAtIsNull(table.getId())).thenReturn(false);
        assertThat(service.closeTable(table.getId(), BigDecimal.ZERO).getStatus()).isEqualTo("CLOSED");
        verify(chipCustodyService).validateTableCustodySettled(any());
    }

    @Test void createUsesServerBusinessDateAndGeneratedUuid() {
        PitTable result = service.createAndOpen(request);
        assertThat(result.getId()).isNotNull();
        assertThat(result.getTableCode()).isEqualTo("T-BAC-10");
        assertThat(result.getBusinessDate()).isEqualTo(businessDate);
        assertThat(result.getStatus()).isEqualTo("OPEN");
        assertThat(result.getOpenedAt()).isNotNull();
        verify(businessDateService).validateBusinessDateIsOpen();
    }

    @Test void duplicateCodeRejected() {
        when(repository.findByTableCodeIgnoreCase("T-BAC-10")).thenReturn(Optional.of(new PitTable()));
        assertThatThrownBy(() -> service.createAndOpen(request)).isInstanceOf(ResourceConflictException.class);
        verify(repository, never()).save(any());
    }

    @Test void systemLockRejectsCreate() {
        when(systemLockService.isSystemLocked()).thenReturn(true);
        assertThatThrownBy(() -> service.createAndOpen(request)).hasMessageContaining("System is locked");
        verify(repository, never()).save(any());
    }

    @Test void nonPitRoleRejected() {
        when(currentUserRoleService.getCurrentUserRole()).thenReturn("DIRECTOR");
        assertThatThrownBy(() -> service.createAndOpen(request)).hasMessageContaining("Only Dealer");
        verify(repository, never()).save(any());
    }

    @Test void closeOpenTablePersistsClosingFloatAndAudit() {
        PitTable table = openTable();
        when(repository.findByIdForUpdate(table.getId())).thenReturn(Optional.of(table));
        when(assignmentRepository.findByPitTableIdAndStatusOrderByJoinedAtAsc(
                table.getId(), com.casino.casinoerp.entity.PitTableCustomerAssignmentStatus.ACTIVE))
                .thenReturn(List.of());

        PitTable result = service.closeTable(table.getId(), new BigDecimal("95000"));

        assertThat(result.getStatus()).isEqualTo("CLOSED");
        assertThat(result.getClosingFloat()).isEqualByComparingTo("95000");
        assertThat(result.getClosedAt()).isNotNull();
        verify(businessDateService).validateSettlementMutationAllowed();
        verify(auditLogService).log(eq("CLOSE_PIT_TABLE"), eq("PIT_TABLE"),
                eq(table.getId()), any(), contains("T-BAC-10"));
    }

    @Test void closeAlreadyClosedTableRejected() {
        PitTable table = openTable(); table.setStatus("CLOSED");
        when(repository.findByIdForUpdate(table.getId())).thenReturn(Optional.of(table));
        assertThatThrownBy(() -> service.closeTable(table.getId(), BigDecimal.ZERO))
                .isInstanceOf(ResourceConflictException.class).hasMessageContaining("already closed");
        verify(repository, never()).save(table);
    }

    @Test void closeWithActiveAssignmentsRejected() {
        PitTable table = openTable();
        when(repository.findByIdForUpdate(table.getId())).thenReturn(Optional.of(table));
        when(assignmentRepository.findByPitTableIdAndStatusOrderByJoinedAtAsc(
                table.getId(), com.casino.casinoerp.entity.PitTableCustomerAssignmentStatus.ACTIVE))
                .thenReturn(List.of(new com.casino.casinoerp.entity.PitTableCustomerAssignment()));
        assertThatThrownBy(() -> service.closeTable(table.getId(), BigDecimal.ZERO))
                .isInstanceOf(ResourceConflictException.class).hasMessageContaining("active customer assignments");
        verify(repository, never()).save(table);
    }

    @Test void closeWithPhysicalTableChipsRejected() {
        PitTable table = openTable();
        when(repository.findByIdForUpdate(table.getId())).thenReturn(Optional.of(table));
        when(assignmentRepository.findByPitTableIdAndStatusOrderByJoinedAtAsc(
                table.getId(), com.casino.casinoerp.entity.PitTableCustomerAssignmentStatus.ACTIVE))
                .thenReturn(List.of());
        doThrow(new ResourceConflictException("All physical Pit Table chips must be returned to the cage"))
                .when(chipCustodyService).validateTableCustodySettled(table.getId());

        assertThatThrownBy(() -> service.closeTable(table.getId(), BigDecimal.ZERO))
                .isInstanceOf(ResourceConflictException.class).hasMessageContaining("physical Pit Table chips");
        verify(repository, never()).save(table);
    }

    @Test void negativeClosingFloatRejected() {
        PitTable table = openTable();
        when(repository.findByIdForUpdate(table.getId())).thenReturn(Optional.of(table));
        assertThatThrownBy(() -> service.closeTable(table.getId(), new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("zero or greater");
    }

    @Test void systemLockRejectsClose() {
        when(systemLockService.isSystemLocked()).thenReturn(true);
        assertThatThrownBy(() -> service.closeTable(UUID.randomUUID(), BigDecimal.ZERO))
                .hasMessageContaining("System is locked");
        verify(repository, never()).save(any());
    }

    @Test void nonexistentTableRejectedOnClose() {
        UUID missing = UUID.randomUUID();
        when(repository.findByIdForUpdate(missing)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.closeTable(missing, BigDecimal.ZERO))
                .hasMessageContaining("Pit table not found");
    }

    @Test void wrongBusinessDateRejectedOnClose() {
        PitTable table = openTable(); table.setBusinessDate(businessDate.minusDays(1));
        when(repository.findByIdForUpdate(table.getId())).thenReturn(Optional.of(table));
        assertThatThrownBy(() -> service.closeTable(table.getId(), BigDecimal.ZERO))
                .isInstanceOf(ResourceConflictException.class).hasMessageContaining("current OPEN Business Date");
    }

    private PitTable openTable() {
        PitTable table = new PitTable(); table.setId(UUID.randomUUID()); table.setTableCode("T-BAC-10");
        table.setStatus("OPEN"); table.setBusinessDate(businessDate); table.setOpeningFloat(new BigDecimal("100000"));
        return table;
    }
}
