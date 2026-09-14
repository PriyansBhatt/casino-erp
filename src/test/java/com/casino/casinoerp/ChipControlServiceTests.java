package com.casino.casinoerp;

import com.casino.casinoerp.entity.BusinessDate;
import com.casino.casinoerp.repository.ChipControlReadRepository;
import com.casino.casinoerp.security.Role;
import com.casino.casinoerp.service.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChipControlServiceTests {
    private final ChipControlReadRepository reads = mock(ChipControlReadRepository.class);
    private final BusinessDateService dates = mock(BusinessDateService.class);
    private final CurrentUserRoleService roles = mock(CurrentUserRoleService.class);
    private final ChipControlService service = new ChipControlService(reads, dates, roles, new RolePermissionService());

    @Test void delegatesOnceToScopedAggregateWithPersistedOpenDate() {
        LocalDate date = LocalDate.of(2026, 9, 2);
        BusinessDate open = new BusinessDate(); open.setBusinessDate(date); open.setStatus("OPEN");
        when(roles.getCurrentRole()).thenReturn(Optional.of(Role.CASHIER));
        when(dates.getCurrentOpenBusinessDate()).thenReturn(Optional.of(open));
        when(reads.directory(date)).thenReturn(List.of());
        var result = service.getCurrentOpenSessionPositions();
        assertThat(result.businessDate()).isEqualTo(date);
        assertThat(result.sessions()).isEmpty();
        verify(reads, times(1)).directory(date);
        verifyNoMoreInteractions(reads);
    }
    @Test void unauthorizedRoleCannotReadFinancialDirectory() {
        when(roles.getCurrentRole()).thenReturn(Optional.of(Role.RECEPTIONIST));
        assertThatThrownBy(service::getCurrentOpenSessionPositions).hasMessageContaining("restricted");
        verifyNoInteractions(reads, dates);
    }
    @Test void noOpenBusinessDateFailsVisibly() {
        when(roles.getCurrentRole()).thenReturn(Optional.of(Role.DIRECTOR));
        when(dates.getCurrentOpenBusinessDate()).thenReturn(Optional.empty());
        assertThatThrownBy(service::getCurrentOpenSessionPositions).hasMessageContaining("not opened");
        verifyNoInteractions(reads);
    }
}
