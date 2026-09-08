package com.casino.casinoerp;

import com.casino.casinoerp.dto.BusinessDateHealthResponse;
import com.casino.casinoerp.entity.BusinessDateHealth;
import com.casino.casinoerp.service.BusinessDateService;
import com.casino.casinoerp.service.OperationalStatusService;
import com.casino.casinoerp.service.SystemLockService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OperationalStatusServiceTests {
    private final BusinessDateService businessDates = mock(BusinessDateService.class);
    private final SystemLockService systemLock = mock(SystemLockService.class);
    private final OperationalStatusService service = new OperationalStatusService(businessDates, systemLock);

    @Test
    void reportsAuthoritativeOpenBusinessDateAndUnlockedState() {
        when(businessDates.getHealth()).thenReturn(health(LocalDate.of(2026, 9, 7),
                BusinessDateHealth.HEALTHY, false, 0, null));
        when(businessDates.currentCasinoDateTime()).thenReturn(LocalDateTime.of(2026, 9, 8, 1, 0));

        var response = service.current();

        assertThat(response.businessDate()).isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(response.expectedBusinessDate()).isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(response.businessDateHealth()).isEqualTo(BusinessDateHealth.HEALTHY);
        assertThat(response.businessDateOpen()).isTrue();
        assertThat(response.systemLocked()).isFalse();
    }

    @Test
    void reportsStaleBusinessDateWithoutChangingSystemLockState() {
        when(businessDates.getHealth()).thenReturn(health(LocalDate.of(2026, 9, 2),
                BusinessDateHealth.STALE, true, 5, "stale"));

        var response = service.current();

        assertThat(response.businessDate()).isEqualTo(LocalDate.of(2026, 9, 2));
        assertThat(response.expectedBusinessDate()).isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(response.businessDateStale()).isTrue();
        assertThat(response.staleByDays()).isEqualTo(5);
        assertThat(response.systemLocked()).isFalse();
    }

    @Test
    void reportsManualLockWithoutExposingEmergencyAdministrationState() {
        when(businessDates.getHealth()).thenReturn(health(null,
                BusinessDateHealth.MISSING, false, 0, "missing"));
        when(systemLock.isSystemLocked()).thenReturn(true);
        when(systemLock.isManuallyLocked()).thenReturn(true);

        var response = service.current();

        assertThat(response.businessDate()).isNull();
        assertThat(response.businessDateOpen()).isFalse();
        assertThat(response.systemLocked()).isTrue();
        assertThat(response.lockReason()).isEqualTo("Manual system lock");
    }


    @Test
    void reportsInconsistentOpenDatesWithoutSelectingAnAuthoritativeDate() {
        when(businessDates.getHealth()).thenReturn(health(null,
                BusinessDateHealth.INCONSISTENT, false, 0, "multiple open dates"));

        var response = service.current();

        assertThat(response.businessDate()).isNull();
        assertThat(response.businessDateOpen()).isFalse();
        assertThat(response.businessDateHealth()).isEqualTo(BusinessDateHealth.INCONSISTENT);
        assertThat(response.lifecycleWarning()).contains("multiple");
    }

    private BusinessDateHealthResponse health(LocalDate open, BusinessDateHealth health,
            boolean stale, long staleByDays, String warning) {
        return new BusinessDateHealthResponse(open, LocalDate.of(2026, 9, 7), health,
                stale, staleByDays, warning);
    }
}
