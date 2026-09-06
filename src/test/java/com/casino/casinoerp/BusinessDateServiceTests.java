package com.casino.casinoerp;

import com.casino.casinoerp.entity.BusinessDate;
import com.casino.casinoerp.repository.BusinessDateRepository;
import com.casino.casinoerp.security.Role;
import com.casino.casinoerp.service.AuditLogService;
import com.casino.casinoerp.service.BusinessDateService;
import com.casino.casinoerp.service.BusinessDateValidationService;
import com.casino.casinoerp.service.CurrentUserRoleService;
import com.casino.casinoerp.service.RolePermissionService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BusinessDateServiceTests {
    @Test
    void closesBusinessDateAfterAuthoritativeRequirementsAreResolved() {
        LocalDate date = LocalDate.of(2026, 8, 8);
        BusinessDateRepository repository = mock(BusinessDateRepository.class);
        BusinessDateValidationService validation = mock(BusinessDateValidationService.class);
        AuditLogService audit = mock(AuditLogService.class);
        CurrentUserRoleService currentRole = mock(CurrentUserRoleService.class);
        BusinessDateService service = new BusinessDateService(repository, validation, audit,
                new RolePermissionService(), currentRole);
        BusinessDate businessDate = new BusinessDate();
        businessDate.setBusinessDate(date);
        businessDate.setStatus("OPEN");
        when(currentRole.getCurrentRole()).thenReturn(Optional.of(Role.SUPER_ADMIN));
        when(repository.findByBusinessDate(date)).thenReturn(Optional.of(businessDate));
        when(validation.validateCloseRequirements(date)).thenReturn(List.of());
        when(repository.save(any(BusinessDate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BusinessDate closed = service.closeBusinessDate(date);

        assertThat(closed.getStatus()).isEqualTo("CLOSED");
        assertThat(closed.getClosedAt()).isNotNull();
        verify(validation).validateCloseRequirements(date);
        verify(repository).save(businessDate);
    }
}
