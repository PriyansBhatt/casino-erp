package com.casino.casinoerp;

import com.casino.casinoerp.dto.CreateChipCashOutRequest;
import com.casino.casinoerp.dto.SessionFinancialPositionResponse;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.repository.*;
import com.casino.casinoerp.security.Role;
import com.casino.casinoerp.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
class ChipCashOutRollbackTests {
    @Autowired ChipCashOutService service;
    @Autowired ChipCashOutRepository cashOutRepository;
    @Autowired WalletTransactionRepository walletRepository;
    @Autowired CustomerSessionRepository sessionRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired UserRepository userRepository;

    @MockitoBean BusinessDateService businessDateService;
    @MockitoBean SystemLockService systemLockService;
    @MockitoBean SessionFinancialPositionService positionService;
    @MockitoBean CurrentUserRoleService currentUserRoleService;
    @MockitoBean AuthenticatedUserService authenticatedUserService;
    @MockitoBean AuditLogService auditLogService;

    @Test
    void auditFailureRollsBackCashOutAndWalletTransaction() {
        CustomerSession session = sessionRepository.findAll().stream()
                .filter(value -> "OPEN".equalsIgnoreCase(value.getStatus()))
                .filter(value -> value.getBusinessDate() != null)
                .filter(value -> customerRepository.findById(value.getCustomerId())
                        .map(customer -> customer.getStatus() == CustomerStatus.ACTIVE).orElse(false))
                .findFirst().orElseThrow();
        User actor = userRepository.findAll().stream().findFirst().orElseThrow();
        UUID customerId = session.getCustomerId();
        String key = "cashout-rollback-" + UUID.randomUUID();

        when(currentUserRoleService.getCurrentUserRole()).thenReturn(Role.CASHIER.name());
        when(businessDateService.getCurrentBusinessDate()).thenReturn(session.getBusinessDate());
        when(authenticatedUserService.getRequiredUser()).thenReturn(actor);
        when(positionService.getPosition(session.getId())).thenReturn(new SessionFinancialPositionResponse(
                customerId, session.getId(), session.getBusinessDate(), new BigDecimal("1000"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1000")));
        doThrow(new RuntimeException("audit failed")).when(auditLogService)
                .log(eq("CREATE_CASH_OUT"), any(), any(), eq(actor.getId()), any());

        long cashOutCount = cashOutRepository.count();
        long walletCount = walletRepository.count();
        CreateChipCashOutRequest request = new CreateChipCashOutRequest(
                customerId, session.getId(), new BigDecimal("100"), new BigDecimal("100"),
                PaymentMode.CASH, null, "Rollback test", key);

        assertThatThrownBy(() -> service.create(request)).hasMessage("audit failed");

        assertThat(cashOutRepository.count()).isEqualTo(cashOutCount);
        assertThat(walletRepository.count()).isEqualTo(walletCount);
        assertThat(cashOutRepository.findByIdempotencyKey(key)).isEmpty();
    }
}
