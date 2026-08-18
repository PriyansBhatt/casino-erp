package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.ChipControlSessionResponse;
import com.casino.casinoerp.dto.ChipControlDirectoryResponse;
import com.casino.casinoerp.dto.SessionFinancialPositionResponse;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class ChipControlService {
    private final CustomerSessionRepository sessionRepository;
    private final CustomerRepository customerRepository;
    private final PitTableCustomerAssignmentRepository assignmentRepository;
    private final PitTableRepository tableRepository;
    private final SessionFinancialPositionService financialPositionService;
    private final BusinessDateService businessDateService;
    private final CurrentUserRoleService currentUserRoleService;
    private final RolePermissionService rolePermissionService;

    public ChipControlService(
            CustomerSessionRepository sessionRepository,
            CustomerRepository customerRepository,
            PitTableCustomerAssignmentRepository assignmentRepository,
            PitTableRepository tableRepository,
            SessionFinancialPositionService financialPositionService,
            BusinessDateService businessDateService,
            CurrentUserRoleService currentUserRoleService,
            RolePermissionService rolePermissionService) {
        this.sessionRepository = sessionRepository;
        this.customerRepository = customerRepository;
        this.assignmentRepository = assignmentRepository;
        this.tableRepository = tableRepository;
        this.financialPositionService = financialPositionService;
        this.businessDateService = businessDateService;
        this.currentUserRoleService = currentUserRoleService;
        this.rolePermissionService = rolePermissionService;
    }

    @Transactional(readOnly = true)
    public ChipControlDirectoryResponse getCurrentOpenSessionPositions() {
        if (!currentUserRoleService.getCurrentRole()
                .map(rolePermissionService::canViewChipControl)
                .orElse(false)) {
            throw new RuntimeException("Access denied. Chip Control financial data is restricted.");
        }

        LocalDate businessDate = businessDateService.getCurrentOpenBusinessDate()
                .orElseThrow(() -> new IllegalStateException("Current business date is not opened."))
                .getBusinessDate();

        List<ChipControlSessionResponse> sessions = sessionRepository.findByStatusIgnoreCaseAndBusinessDateOrderByEntryTimeAsc(
                        "OPEN", businessDate).stream()
                .map(this::toResponse)
                .toList();
        return new ChipControlDirectoryResponse(businessDate, sessions);
    }

    private ChipControlSessionResponse toResponse(CustomerSession session) {
        Customer customer = customerRepository.findById(session.getCustomerId())
                .orElseThrow(() -> new IllegalStateException("Customer for active session was not found."));
        SessionFinancialPositionResponse position = financialPositionService.getPosition(session.getId());
        PitTableCustomerAssignment assignment = assignmentRepository.findByCustomerSessionIdAndStatus(
                session.getId(), PitTableCustomerAssignmentStatus.ACTIVE).orElse(null);
        PitTable table = assignment == null ? null : tableRepository.findById(assignment.getPitTableId())
                .orElseThrow(() -> new IllegalStateException("Active Pit Table assignment references a missing table."));

        String exposureStatus = assignment != null
                ? "AT_TABLE"
                : position.calculatedChipPosition().compareTo(BigDecimal.ZERO) > 0
                    ? "OUTSTANDING"
                    : "CLEAR";

        return new ChipControlSessionResponse(
                customer.getId(), customer.getCustomerCode(), customer.getFullName(),
                session.getId(), session.getSessionCode(), session.getBusinessDate(),
                session.getEntryTime(), session.getStatus(), null,
                table == null ? null : table.getId(),
                table == null ? null : table.getTableCode(),
                table == null ? null : table.getTableName(),
                position.totalBuyIn(), position.verifiedGamingWin(),
                position.verifiedGamingLoss(), position.totalCashOut(),
                position.calculatedChipPosition(), exposureStatus
        );
    }
}
