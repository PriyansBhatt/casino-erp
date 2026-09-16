package com.casino.casinoerp;

import com.casino.casinoerp.dto.SessionFinancialPositionResponse;
import com.casino.casinoerp.entity.CustomerSession;
import com.casino.casinoerp.exception.ResourceConflictException;
import com.casino.casinoerp.repository.*;
import com.casino.casinoerp.security.Role;
import com.casino.casinoerp.service.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real PostgreSQL lifecycle locking with in-memory entity fixtures; no operational writes or app startup. */
class I1aExitLockTests {
    private static void lock(Connection c) throws Exception {
        String sql = BusinessDateRepository.class.getMethod("acquireLifecycleLock").getAnnotation(Query.class).value();
        try (var st = c.createStatement(); var rs = st.executeQuery(sql)) { assertThat(rs.next()).isTrue(); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTODY", "SLOT"})
    void exitWaitsForDependencyMutationThenRechecksAndSucceedsAfterResolution(String dependency) throws Exception {
        try (var owner = I1aReadBoundaryTests.connection(); var waiter = I1aReadBoundaryTests.connection();
             var executor = Executors.newSingleThreadExecutor()) {
            int pid;
            try (var st = waiter.createStatement(); var rs = st.executeQuery("select pg_backend_pid()")) {
                rs.next(); pid = rs.getInt(1);
            }
            var sessions = mock(CustomerSessionRepository.class);
            var dates = mock(BusinessDateService.class);
            var custody = mock(ChipCustodyInventoryRepository.class);
            var machines = mock(MachineRepository.class);
            var roles = mock(CurrentUserRoleService.class);
            var finances = mock(SessionFinancialPositionService.class);
            var hasDependency = new AtomicBoolean(false);
            var attempted = new CountDownLatch(1);
            var session = new CustomerSession();
            session.setId(UUID.randomUUID()); session.setCustomerId(UUID.randomUUID());
            session.setStatus("OPEN"); session.setBusinessDate(LocalDate.of(2026,9,2));
            when(sessions.findByIdForUpdate(session.getId())).thenReturn(Optional.of(session));
            when(sessions.save(session)).thenReturn(session);
            when(roles.getCurrentRole()).thenReturn(Optional.of(Role.RECEPTIONIST));
            when(finances.getPosition(session.getId())).thenReturn(new SessionFinancialPositionResponse(
                    session.getCustomerId(), session.getId(), session.getBusinessDate(),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
            when(custody.hasCustomerSessionChips(session.getId()))
                    .thenAnswer(i -> dependency.equals("CUSTODY") && hasDependency.get());
            when(machines.hasActivePlay(session.getId()))
                    .thenAnswer(i -> dependency.equals("SLOT") && hasDependency.get());
            doAnswer(i -> { attempted.countDown(); lock(waiter); return null; })
                    .when(dates).validateSettlementMutationAllowed();
            var service = new CustomerSessionService(sessions, dates, mock(SystemLockService.class),
                    mock(AuditLogService.class), new RolePermissionService(), roles, mock(CustomerService.class),
                    mock(PitTableCustomerAssignmentRepository.class), finances, custody, machines);
            lock(owner);
            Future<?> exit = executor.submit(() -> assertThatThrownBy(() -> service.closeSession(session.getId()))
                    .isInstanceOf(ResourceConflictException.class));
            try {
                assertThat(attempted.await(5, TimeUnit.SECONDS)).isTrue();
                boolean waiting = false;
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (!waiting && System.nanoTime() < deadline) {
                    try (var st = owner.prepareStatement("select exists(select 1 from pg_locks where pid=? and locktype='advisory' and not granted)")) {
                        st.setInt(1,pid);
                        try (var rs = st.executeQuery()) { rs.next(); waiting = rs.getBoolean(1); }
                    }
                    if (!waiting) Thread.sleep(10);
                }
                assertThat(waiting).as("exit must wait at the production lifecycle lock").isTrue();
                verifyNoInteractions(custody, machines);
                hasDependency.set(true);
            } finally { owner.rollback(); }
            try {
                exit.get(5,TimeUnit.SECONDS);
                verify(sessions,never()).save(any());
                hasDependency.set(false);
                assertThat(service.closeSession(session.getId()).status()).isEqualTo("CLOSED");
            } finally { waiter.rollback(); }
        }
    }
}
