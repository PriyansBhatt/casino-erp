package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.entity.BusinessDate;
import com.casino.casinoerp.exception.ResourceNotFoundException;
import com.casino.casinoerp.repository.*;
import com.casino.casinoerp.security.Role;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.List;

@Service
public class RunningFundsReportService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Kathmandu");
    private final BusinessDateRepository dates;
    private final BusinessDateService businessDates;
    private final ManagementReportReadRepository reads;
    private final CurrentUserRoleService roles;
    public RunningFundsReportService(BusinessDateRepository dates, BusinessDateService businessDates,
            ManagementReportReadRepository reads, CurrentUserRoleService roles) {
        this.dates = dates; this.businessDates = businessDates; this.reads = reads; this.roles = roles;
    }
    private BusinessDate select(LocalDate requested) {
        if (roles.getCurrentRole().filter(r -> r == Role.DIRECTOR || r == Role.SUPER_ADMIN).isEmpty())
            throw new AccessDeniedException("Reports are restricted to management.");
        return requested == null ? businessDates.getCurrentOpenBusinessDate()
                .orElseThrow(() -> new IllegalArgumentException("No Business Date is open. Select an existing historical Business Date."))
                : dates.findByBusinessDate(requested).orElseThrow(() -> new ResourceNotFoundException("Business Date not found."));
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public RunningFundsReportResponse getReport(LocalDate requested) {
        var selected = select(requested); var date = selected.getBusinessDate();
        var guests = reads.guests(date); var payments = reads.payments(date);
        var payouts = reads.payouts(date); var gaming = reads.gaming(date); var rec = reads.reconciliations(date);
        return new RunningFundsReportResponse(date, selected.getStatus(), date.atTime(9,0).atZone(ZONE).toOffsetDateTime(),
                date.plusDays(1).atTime(9,0).atZone(ZONE).toOffsetDateTime(), guests.entries(), guests.distinctGuests(),
                payments.buyIn(), payments.cashOut(), payouts.count(), payouts.amount(), gaming.wins(), gaming.losses(),
                rec.submitted(), rec.reopened(), rec.submitted() == 0 ? null : rec.variance());
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ReconciliationReportPage reconciliations(LocalDate requested, int page, int size) {
        var selected = select(requested);
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Page must be nonnegative and size between 1 and 100.");
        var rows = reads.rows(selected.getBusinessDate(), page, size);
        return new ReconciliationReportPage(selected.getBusinessDate(), selected.getStatus(),
                List.copyOf(rows.subList(0, Math.min(size, rows.size()))), page, size, rows.size() > size);
    }
}
