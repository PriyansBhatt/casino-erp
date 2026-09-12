package com.casino.casinoerp;

import com.casino.casinoerp.dto.ManagementDashboardResponse;
import com.casino.casinoerp.entity.BusinessDate;
import com.casino.casinoerp.exception.ResourceNotFoundException;
import com.casino.casinoerp.repository.*;
import com.casino.casinoerp.security.Role;
import com.casino.casinoerp.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.*;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManagementDashboardServiceTests {
    private final BusinessDateService lifecycle = mock(BusinessDateService.class);
    private final BusinessDateRepository dates = mock(BusinessDateRepository.class);
    private final ManagementDashboardRepository repository = mock(ManagementDashboardRepository.class);
    private final CurrentUserRoleService roles = mock(CurrentUserRoleService.class);
    private final LocalDate date = LocalDate.of(2026, 9, 1);
    // It is September 2, 08:59 in Kathmandu: the open date remains September 1.
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-02T03:14:00Z"), ZoneOffset.UTC);
    private final ManagementDashboardService service = new ManagementDashboardService(lifecycle, dates, repository, roles, clock);

    @BeforeEach void setUp() {
        when(roles.getCurrentRole()).thenReturn(Optional.of(Role.SUPER_ADMIN));
        when(lifecycle.getCurrentOpenBusinessDate()).thenReturn(Optional.of(businessDate(date, "OPEN")));
        when(repository.payments(date)).thenReturn(new ManagementDashboardRepository.PaymentTotals(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        when(repository.reconciliations(date)).thenReturn(new ManagementDashboardRepository.ReconciliationAggregate(0, BigDecimal.ZERO));
    }

    @Test void emptyDateHasRealZerosButNoInventedVarianceOrUnsupportedMetrics() {
        ManagementDashboardResponse result = service.getDashboard(null);
        for (String key : new String[]{"activeCustomers", "buyInTotal", "cashOutTotal", "losingReturnPaidTotal", "activeTables"}) {
            assertThat(result.summary().get(key).available()).isTrue();
            assertThat(result.summary().get(key).value()).isEqualByComparingTo(BigDecimal.ZERO);
        }
        for (String key : new String[]{"cashierVariance", "activeMachines", "unresolvedChipValue", "cashIncome", "cashExpense", "purchaseApprovals", "billsPending"}) {
            assertThat(result.summary().get(key).available()).isFalse();
            assertThat(result.summary().get(key).value()).isNull();
            assertThat(result.summary().get(key).description()).isNotBlank();
        }
    }

    @Test void usesOpenLifecycleDateAndCrossMidnightKathmanduWindow() {
        var result = service.getDashboard(null);
        assertThat(result.businessDate()).isEqualTo(date);
        assertThat(result.businessDateStatus()).isEqualTo("OPEN");
        assertThat(result.timeZone()).isEqualTo("Asia/Kathmandu");
        assertThat(result.windowStart()).isEqualTo(OffsetDateTime.parse("2026-09-01T09:00:00+05:45"));
        assertThat(result.windowEndExclusive()).isEqualTo(OffsetDateTime.parse("2026-09-02T09:00:00+05:45"));
        assertThat(result.lastUpdated()).isEqualTo(OffsetDateTime.parse("2026-09-02T08:59:00+05:45"));
        verify(repository).payments(date);
        verifyNoInteractions(dates);
    }

    @Test void staleOpenDateIsNotReplacedByCalendarDate() {
        var later = new ManagementDashboardService(lifecycle, dates, repository, roles,
                Clock.fixed(Instant.parse("2026-09-10T12:00:00Z"), ZoneOffset.UTC));
        assertThat(later.getDashboard(null).businessDate()).isEqualTo(date);
        verify(repository).payments(date);
    }

    @Test void selectsExistingHistoricalClosedDate() {
        when(dates.findByBusinessDate(date)).thenReturn(Optional.of(businessDate(date, "CLOSED")));
        var result = service.getDashboard(date);
        assertThat(result.businessDate()).isEqualTo(date);
        assertThat(result.businessDateStatus()).isEqualTo("CLOSED");
        verifyNoInteractions(lifecycle);
        verify(repository).payments(date);
        verify(repository).activeCustomers(date);
        verify(repository).activeTables(date);
        verify(repository).reconciliations(date);
    }

    @Test void noOpenDateIsUnavailableAndDoesNotQueryOperationalTables() {
        when(lifecycle.getCurrentOpenBusinessDate()).thenReturn(Optional.empty());
        var result = service.getDashboard(null);
        assertThat(result.businessDate()).isNull();
        assertThat(result.businessDateStatus()).isEqualTo("NOT_OPEN");
        assertThat(result.windowStart()).isNull();
        assertThat(result.windowEndExclusive()).isNull();
        assertThat(result.summary().values()).allSatisfy(metric -> {
            assertThat(metric.available()).isFalse();
            assertThat(metric.value()).isNull();
        });
        verifyNoInteractions(repository);
    }

    @Test void unknownHistoricalDateFailsWithoutFallingBackToToday() {
        when(dates.findByBusinessDate(date)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getDashboard(date)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(repository, lifecycle);
    }

    @Test void exposesAggregatesAndSubmittedVarianceWithoutRecomputingAccounting() {
        when(repository.payments(date)).thenReturn(new ManagementDashboardRepository.PaymentTotals(
                new BigDecimal("1250.25"), new BigDecimal("240.50"), new BigDecimal("10.00")));
        when(repository.activeCustomers(date)).thenReturn(3L);
        when(repository.activeTables(date)).thenReturn(2L);
        when(repository.reconciliations(date)).thenReturn(new ManagementDashboardRepository.ReconciliationAggregate(2, new BigDecimal("-25.50")));
        var result = service.getDashboard(null);
        assertThat(result.summary().get("buyInTotal").value()).isEqualByComparingTo("1250.25");
        assertThat(result.summary().get("cashOutTotal").value()).isEqualByComparingTo("240.50");
        assertThat(result.summary().get("losingReturnPaidTotal").value()).isEqualByComparingTo("10");
        assertThat(result.summary().get("activeCustomers").value()).isEqualByComparingTo("3");
        assertThat(result.summary().get("activeTables").value()).isEqualByComparingTo("2");
        assertThat(result.summary().get("cashierVariance").available()).isTrue();
        assertThat(result.summary().get("cashierVariance").value()).isEqualByComparingTo("-25.50");
    }

    @Test void unauthorizedServiceCallCannotReadData() {
        when(roles.getCurrentRole()).thenReturn(Optional.of(Role.CASHIER));
        assertThatThrownBy(() -> service.getDashboard(null)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(lifecycle, dates, repository);
    }

    private BusinessDate businessDate(LocalDate date, String status) {
        BusinessDate value = new BusinessDate();
        value.setBusinessDate(date);
        value.setStatus(status);
        return value;
    }
}
