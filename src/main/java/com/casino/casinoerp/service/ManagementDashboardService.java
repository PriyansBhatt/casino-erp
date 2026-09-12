package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.ManagementDashboardResponse;
import com.casino.casinoerp.dto.ManagementDashboardResponse.Metric;
import com.casino.casinoerp.entity.BusinessDate;
import com.casino.casinoerp.exception.ResourceNotFoundException;
import com.casino.casinoerp.repository.BusinessDateRepository;
import com.casino.casinoerp.repository.ManagementDashboardRepository;
import com.casino.casinoerp.security.Role;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
public class ManagementDashboardService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Kathmandu");
    private final BusinessDateService businessDates;
    private final BusinessDateRepository dates;
    private final ManagementDashboardRepository repository;
    private final CurrentUserRoleService roles;
    private final Clock clock;

    public ManagementDashboardService(BusinessDateService businessDates, BusinessDateRepository dates,
            ManagementDashboardRepository repository, CurrentUserRoleService roles, Clock clock) {
        this.businessDates = businessDates;
        this.dates = dates;
        this.repository = repository;
        this.roles = roles;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ManagementDashboardResponse getDashboard(LocalDate requestedDate) {
        if (roles.getCurrentRole().filter(role -> role == Role.DIRECTOR || role == Role.SUPER_ADMIN).isEmpty()) {
            throw new AccessDeniedException("Management dashboard is restricted to DIRECTOR and SUPER_ADMIN.");
        }
        BusinessDate selected = requestedDate == null
                ? businessDates.getCurrentOpenBusinessDate().orElse(null)
                : dates.findByBusinessDate(requestedDate)
                    .orElseThrow(() -> new ResourceNotFoundException("Business Date not found."));
        LocalDate date = selected == null ? null : selected.getBusinessDate();
        Map<String, Metric> summary = new LinkedHashMap<>();
        if (date == null) {
            for (String key : List.of("activeCustomers", "buyInTotal", "cashOutTotal",
                    "losingReturnPaidTotal", "activeTables", "cashierVariance")) {
                summary.put(key, Metric.unavailable("No Business Date is open. Select an existing Business Date."));
            }
        } else {
            var payments = repository.payments(date);
            summary.put("activeCustomers", Metric.available(BigDecimal.valueOf(repository.activeCustomers(date)),
                    "Distinct customers with OPEN, unexited sessions for this Business Date; current persisted state, not a historical snapshot."));
            summary.put("buyInTotal", Metric.available(payments.buyIn(), "Posted buy-in receipts, all payment modes; not accounting income."));
            summary.put("cashOutTotal", Metric.available(payments.cashOut(), "Posted customer cash-outs, all payment modes; excludes Losing Returns."));
            summary.put("losingReturnPaidTotal", Metric.available(payments.losingReturnPaid(), "Persisted Losing Return payments only."));
            summary.put("activeTables", Metric.available(BigDecimal.valueOf(repository.activeTables(date)),
                    "Physical tables with an OPEN, unclosed session for this Business Date; current persisted state, not a historical snapshot."));
            var reconciliation = repository.reconciliations(date);
            summary.put("cashierVariance", reconciliation.submittedCount() == 0
                    ? Metric.unavailable("No submitted cashier reconciliations for this Business Date.")
                    : Metric.available(reconciliation.variance(), "Total variance of submitted reconciliations for this Business Date; reopened records excluded."));
        }
        summary.put("activeMachines", Metric.unavailable("Machine gaming has no authoritative backend module."));
        summary.put("unresolvedChipValue", Metric.unavailable("An efficient bulk authoritative Chip Control exposure summary is not available."));
        summary.put("cashIncome", Metric.unavailable("Accounting income is not implemented; cashier collections are not accounting income."));
        summary.put("cashExpense", Metric.unavailable("Operating expense accounting is not implemented."));
        summary.put("purchaseApprovals", Metric.unavailable("Purchase approval workflow is not implemented in the backend."));
        summary.put("billsPending", Metric.unavailable("Bill verification workflow is not implemented in the backend."));
        return new ManagementDashboardResponse(date, selected == null ? "NOT_OPEN" : selected.getStatus(),
                OffsetDateTime.now(clock.withZone(ZONE)), ZONE.getId(),
                date == null ? null : date.atTime(9, 0).atZone(ZONE).toOffsetDateTime(),
                date == null ? null : date.plusDays(1).atTime(9, 0).atZone(ZONE).toOffsetDateTime(),
                Collections.unmodifiableMap(summary));
    }
}
