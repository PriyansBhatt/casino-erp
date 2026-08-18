package com.casino.casinoerp;

import com.casino.casinoerp.dto.CreateCustomerBonusRequest;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.repository.*;
import com.casino.casinoerp.security.Role;
import com.casino.casinoerp.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomerBonusServiceTests {
    private final CustomerBonusRepository bonuses = mock(CustomerBonusRepository.class);
    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final CustomerSessionRepository sessions = mock(CustomerSessionRepository.class);
    private final BusinessDateRepository dateRepository = mock(BusinessDateRepository.class);
    private final BusinessDateService dates = mock(BusinessDateService.class);
    private final SystemLockService lock = mock(SystemLockService.class);
    private final AuthenticatedUserService authenticatedUsers = mock(AuthenticatedUserService.class);
    private final CurrentUserRoleService roles = mock(CurrentUserRoleService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final CustomerBonusService service = new CustomerBonusService(bonuses, customers, sessions,
            dateRepository, dates, lock, authenticatedUsers, roles, new RolePermissionService(), users, audit);
    private final UUID customerId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final LocalDate businessDate = LocalDate.of(2026, 8, 8);

    @BeforeEach
    void setUp() {
        when(roles.getCurrentRole()).thenReturn(Optional.of(Role.DIRECTOR));
        when(dates.getCurrentBusinessDate()).thenReturn(businessDate);
        when(customers.findById(customerId)).thenReturn(Optional.of(customer()));
        when(sessions.findById(sessionId)).thenReturn(Optional.of(session(customerId, "OPEN", businessDate)));
        when(authenticatedUsers.getRequiredUser()).thenReturn(actor());
        when(bonuses.save(any())).thenAnswer(invocation -> {
            CustomerBonus value = invocation.getArgument(0); value.setId(UUID.randomUUID()); return value;
        });
    }

    @Test
    void directorCreatesImmediatelyApprovedAuthoritativeBonusAndAudit() {
        var response = service.create(request("2500"));

        assertThat(response.businessDate()).isEqualTo(businessDate);
        assertThat(response.status()).isEqualTo(CustomerBonusStatus.APPROVED);
        assertThat(response.createdBy().id()).isEqualTo(actorId);
        assertThat(response.approvedBy().id()).isEqualTo(actorId);
        verify(bonuses).save(argThat(value -> value.getBonusCode().startsWith("BON-20260808-")
                && value.getBusinessDate().equals(businessDate)
                && value.getCreatedAt() != null && value.getApprovedAt() != null));
        verify(audit).log(eq("CREATE_CUSTOMER_BONUS"), eq("CUSTOMER_BONUS"), any(), eq(actorId), contains("amount=2500"));
    }

    @Test
    void superAdminIsAllowed() {
        when(roles.getCurrentRole()).thenReturn(Optional.of(Role.SUPER_ADMIN));
        assertThat(service.create(request("1000")).status()).isEqualTo(CustomerBonusStatus.APPROVED);
    }

    @Test
    void unauthorizedRoleIsDeniedBeforeWrites() {
        when(roles.getCurrentRole()).thenReturn(Optional.of(Role.CASHIER));
        assertThatThrownBy(() -> service.create(request("1000"))).hasMessageContaining("restricted to Director or Super Admin");
        verifyNoInteractions(bonuses);
    }

    @Test
    void invalidCustomerIsRejected() {
        when(customers.findById(customerId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(request("1000"))).hasMessage("Customer not found.");
        verify(bonuses, never()).save(any());
    }

    @Test
    void sessionBelongingToAnotherCustomerIsRejected() {
        when(sessions.findById(sessionId)).thenReturn(Optional.of(session(UUID.randomUUID(), "OPEN", businessDate)));
        assertThatThrownBy(() -> service.create(request("1000"))).hasMessageContaining("does not belong");
    }

    @Test
    void closedOrWrongBusinessDateSessionIsRejected() {
        when(sessions.findById(sessionId)).thenReturn(Optional.of(session(customerId, "CLOSED", businessDate)));
        assertThatThrownBy(() -> service.create(request("1000"))).hasMessageContaining("must be OPEN");
    }

    @Test
    void systemLockBlocksBonusBeforePersistence() {
        when(lock.isSystemLocked()).thenReturn(true);
        assertThatThrownBy(() -> service.create(request("1000"))).hasMessageContaining("System is locked");
        verify(bonuses, never()).save(any());
        verifyNoInteractions(audit);
    }

    @Test
    void matchingIdempotentReplayDoesNotCreateOrAuditAgain() {
        CustomerBonus existing = new CustomerBonus(); existing.setId(UUID.randomUUID());
        existing.setBonusCode("BON-existing"); existing.setCustomerId(customerId); existing.setCustomerSessionId(sessionId);
        existing.setBusinessDate(businessDate); existing.setBonusType(CustomerBonusType.PROMOTIONAL);
        existing.setAmount(new BigDecimal("2500")); existing.setReason("Promotional campaign");
        existing.setStatus(CustomerBonusStatus.APPROVED); existing.setCreatedBy(actorId); existing.setApprovedBy(actorId);
        existing.setCreatedAt(java.time.LocalDateTime.now()); existing.setApprovedAt(java.time.LocalDateTime.now());
        when(bonuses.findByIdempotencyKey("bonus-key-1")).thenReturn(Optional.of(existing));
        when(users.findAllById(any())).thenReturn(List.of(actor()));

        assertThat(service.create(request("2500")).id()).isEqualTo(existing.getId());
        verify(bonuses, never()).save(any());
        verifyNoInteractions(audit);
    }

    @Test
    void businessDateHistoryIsIsolatedByExactDate() {
        BusinessDate value = new BusinessDate(); value.setBusinessDate(businessDate);
        when(dateRepository.findByBusinessDate(businessDate)).thenReturn(Optional.of(value));
        when(bonuses.findByBusinessDateOrderByCreatedAtDesc(businessDate)).thenReturn(List.of());
        when(customers.findAllById(any())).thenReturn(List.of());
        when(sessions.findAllById(any())).thenReturn(List.of());
        when(users.findAllById(any())).thenReturn(List.of());

        assertThat(service.getByBusinessDate(businessDate)).isEmpty();
        verify(bonuses).findByBusinessDateOrderByCreatedAtDesc(businessDate);
        verify(bonuses, never()).findAll();
    }

    private CreateCustomerBonusRequest request(String amount) {
        return new CreateCustomerBonusRequest(customerId, sessionId, CustomerBonusType.PROMOTIONAL,
                new BigDecimal(amount), "Promotional campaign", "bonus-key-1");
    }
    private Customer customer() {
        Customer value = new Customer(); value.setId(customerId); value.setCustomerCode("CUS-1001");
        value.setFullName("Test Customer"); value.setStatus(CustomerStatus.ACTIVE); return value;
    }
    private CustomerSession session(UUID owner, String status, LocalDate date) {
        CustomerSession value = new CustomerSession(); value.setId(sessionId); value.setCustomerId(owner);
        value.setSessionCode("SES-1"); value.setStatus(status); value.setBusinessDate(date); return value;
    }
    private User actor() {
        User value = new User(); value.setId(actorId); value.setUsername("director"); return value;
    }
}
