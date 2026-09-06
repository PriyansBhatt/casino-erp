package com.casino.casinoerp;

import com.casino.casinoerp.entity.BusinessDate;
import com.casino.casinoerp.service.BusinessDateService;
import com.casino.casinoerp.service.OperationalStatusService;
import com.casino.casinoerp.service.SystemLockService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OperationalStatusServiceTests {
    private final BusinessDateService businessDates = mock(BusinessDateService.class);
    private final SystemLockService systemLock = mock(SystemLockService.class);
    private final OperationalStatusService service = new OperationalStatusService(businessDates, systemLock);

    @Test
    void reportsAuthoritativeOpenBusinessDateAndUnlockedState() {
        BusinessDate value = new BusinessDate(); value.setBusinessDate(LocalDate.of(2026, 9, 2));
        when(businessDates.getCurrentOpenBusinessDate()).thenReturn(Optional.of(value));

        var response = service.current();

        assertThat(response.businessDate()).isEqualTo(LocalDate.of(2026, 9, 2));
        assertThat(response.businessDateOpen()).isTrue();
        assertThat(response.systemLocked()).isFalse();
    }

    @Test
    void reportsManualLockWithoutExposingEmergencyAdministrationState() {
        when(businessDates.getCurrentOpenBusinessDate()).thenReturn(Optional.empty());
        when(systemLock.isSystemLocked()).thenReturn(true);
        when(systemLock.isManuallyLocked()).thenReturn(true);

        var response = service.current();

        assertThat(response.businessDate()).isNull();
        assertThat(response.businessDateOpen()).isFalse();
        assertThat(response.systemLocked()).isTrue();
        assertThat(response.lockReason()).isEqualTo("Manual system lock");
    }
}
