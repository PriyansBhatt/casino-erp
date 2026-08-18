package com.casino.casinoerp;

import com.casino.casinoerp.dto.SessionFinancialPositionResponse;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.repository.*;
import com.casino.casinoerp.security.Role;
import com.casino.casinoerp.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ChipControlServiceTests {
    private final CustomerSessionRepository sessionRepository = mock(CustomerSessionRepository.class);
    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final PitTableCustomerAssignmentRepository assignmentRepository = mock(PitTableCustomerAssignmentRepository.class);
    private final PitTableRepository tableRepository = mock(PitTableRepository.class);
    private final SessionFinancialPositionService positionService = mock(SessionFinancialPositionService.class);
    private final BusinessDateService businessDateService = mock(BusinessDateService.class);
    private final CurrentUserRoleService currentUserRoleService = mock(CurrentUserRoleService.class);
    private final ChipControlService service = new ChipControlService(
            sessionRepository, customerRepository, assignmentRepository, tableRepository,
            positionService, businessDateService, currentUserRoleService, new RolePermissionService());
    private final LocalDate businessDate = LocalDate.of(2026, 8, 8);
    private final UUID customerId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();

    @BeforeEach void setUp() {
        when(currentUserRoleService.getCurrentRole()).thenReturn(Optional.of(Role.CASHIER));
        BusinessDate open = new BusinessDate(); open.setBusinessDate(businessDate); open.setStatus("OPEN");
        when(businessDateService.getCurrentOpenBusinessDate()).thenReturn(Optional.of(open));
        CustomerSession session = new CustomerSession(); session.setId(sessionId); session.setCustomerId(customerId);
        session.setSessionCode("SES-1001"); session.setStatus("OPEN"); session.setBusinessDate(businessDate);
        session.setEntryTime(LocalDateTime.of(2026, 8, 8, 18, 0));
        when(sessionRepository.findByStatusIgnoreCaseAndBusinessDateOrderByEntryTimeAsc("OPEN", businessDate))
                .thenReturn(List.of(session));
        Customer customer = new Customer(); customer.setId(customerId); customer.setCustomerCode("CUS-1001");
        customer.setFullName("Test Customer");
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(positionService.getPosition(sessionId)).thenReturn(new SessionFinancialPositionResponse(
                customerId, sessionId, businessDate, new BigDecimal("1000"), new BigDecimal("500"),
                new BigDecimal("3000"), BigDecimal.ZERO, new BigDecimal("3500")));
        when(assignmentRepository.findByCustomerSessionIdAndStatus(
                sessionId, PitTableCustomerAssignmentStatus.ACTIVE)).thenReturn(Optional.empty());
    }

    @Test void composesAuthoritativePositionForCurrentOpenBusinessDate() {
        var result = service.getCurrentOpenSessionPositions();
        assertThat(result.businessDate()).isEqualTo(businessDate);
        assertThat(result.sessions()).hasSize(1);
        assertThat(result.sessions().getFirst().customerCode()).isEqualTo("CUS-1001");
        assertThat(result.sessions().getFirst().calculatedChipPosition()).isEqualByComparingTo("3500");
        assertThat(result.sessions().getFirst().exposureStatus()).isEqualTo("OUTSTANDING");
        assertThat(result.sessions().getFirst().activeTableId()).isNull();
        verify(positionService).getPosition(sessionId);
        verify(sessionRepository).findByStatusIgnoreCaseAndBusinessDateOrderByEntryTimeAsc("OPEN", businessDate);
    }

    @Test void activeAssignmentAppearsButLeftAssignmentDoesNot() {
        UUID tableId = UUID.randomUUID();
        PitTableCustomerAssignment assignment = new PitTableCustomerAssignment();
        assignment.setPitTableId(tableId); assignment.setStatus(PitTableCustomerAssignmentStatus.ACTIVE);
        PitTable table = new PitTable(); table.setId(tableId); table.setTableCode("T-BAC-11");
        table.setTableName("Baccarat Table 11"); table.setStatus("OPEN");
        when(assignmentRepository.findByCustomerSessionIdAndStatus(
                sessionId, PitTableCustomerAssignmentStatus.ACTIVE)).thenReturn(Optional.of(assignment));
        when(tableRepository.findById(tableId)).thenReturn(Optional.of(table));

        var result = service.getCurrentOpenSessionPositions().sessions().getFirst();
        assertThat(result.exposureStatus()).isEqualTo("AT_TABLE");
        assertThat(result.activeTableCode()).isEqualTo("T-BAC-11");
    }

    @Test void unauthorizedRoleCannotReadFinancialDirectory() {
        when(currentUserRoleService.getCurrentRole()).thenReturn(Optional.of(Role.RECEPTIONIST));
        assertThatThrownBy(service::getCurrentOpenSessionPositions).hasMessageContaining("restricted");
        verifyNoInteractions(sessionRepository);
    }

    @Test void noOpenBusinessDateFailsVisibly() {
        when(businessDateService.getCurrentOpenBusinessDate()).thenReturn(Optional.empty());
        assertThatThrownBy(service::getCurrentOpenSessionPositions).hasMessageContaining("not opened");
    }
}
