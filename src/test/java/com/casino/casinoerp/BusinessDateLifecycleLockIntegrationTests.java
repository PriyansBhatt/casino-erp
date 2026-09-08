package com.casino.casinoerp;

import com.casino.casinoerp.entity.BusinessDate;
import com.casino.casinoerp.exception.ResourceConflictException;
import com.casino.casinoerp.repository.BusinessDateRepository;
import com.casino.casinoerp.service.AuditLogService;
import com.casino.casinoerp.service.BusinessDateService;
import com.casino.casinoerp.service.BusinessDateValidationService;
import com.casino.casinoerp.service.CurrentUserRoleService;
import com.casino.casinoerp.service.RolePermissionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
class BusinessDateLifecycleLockIntegrationTests {

    @Autowired
    private BusinessDateRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void postgresLifecycleLockSerializesConcurrentTransactions() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var firstHasLock = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        var secondHasLock = new CountDownLatch(1);
        var activeCriticalSections = new AtomicInteger();
        var maximumConcurrentCriticalSections = new AtomicInteger();
        try {
            var first = executor.submit(() -> inTransaction(() -> {
                repository.acquireLifecycleLock();
                enter(activeCriticalSections, maximumConcurrentCriticalSections);
                firstHasLock.countDown();
                await(releaseFirst);
                activeCriticalSections.decrementAndGet();
            }));
            assertThat(firstHasLock.await(5, TimeUnit.SECONDS)).isTrue();

            var second = executor.submit(() -> inTransaction(() -> {
                repository.acquireLifecycleLock();
                enter(activeCriticalSections, maximumConcurrentCriticalSections);
                secondHasLock.countDown();
                activeCriticalSections.decrementAndGet();
            }));

            assertThat(secondHasLock.await(250, TimeUnit.MILLISECONDS)).isFalse();
            releaseFirst.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);

            assertThat(secondHasLock.getCount()).isZero();
            assertThat(maximumConcurrentCriticalSections).hasValue(1);
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void closeSerializesRepresentativeNewObligationsAndForcesStateRevalidation() throws Exception {
        for (String operation : List.of("BUY_IN", "SESSION_OPEN", "PIT_OPEN", "CUSTODY_ISSUE")) {
            String error = mutationWaitingForClose(false);
            assertThat(error).as(operation)
                    .isEqualTo("No operational Business Date is OPEN. New operations are disabled.");
        }
    }

    @Test
    void settlementWaitingForCloseRevalidatesAndCannotPostToClosedDate() throws Exception {
        assertThat(mutationWaitingForClose(true)).isEqualTo(
                "No operational Business Date is OPEN. Settlement operations requiring an OPEN Business Date are disabled.");
    }

    private String mutationWaitingForClose(boolean settlement) throws Exception {
        BusinessDateRepository guardedRepository = mock(BusinessDateRepository.class);
        AtomicReference<List<BusinessDate>> openDates = new AtomicReference<>(List.of(openDate()));
        when(guardedRepository.acquireLifecycleLock()).thenAnswer(invocation -> repository.acquireLifecycleLock());
        when(guardedRepository.findByStatus("OPEN")).thenAnswer(invocation -> openDates.get());
        BusinessDateService service = new BusinessDateService(
                guardedRepository, mock(BusinessDateValidationService.class), mock(AuditLogService.class),
                new RolePermissionService(), mock(CurrentUserRoleService.class),
                Clock.fixed(Instant.parse("2026-09-08T12:15:00Z"), ZoneOffset.UTC));

        var executor = Executors.newFixedThreadPool(2);
        var closeHasLock = new CountDownLatch(1);
        var releaseClose = new CountDownLatch(1);
        var mutationFinished = new CountDownLatch(1);
        try {
            var close = executor.submit(() -> inTransaction(() -> {
                repository.acquireLifecycleLock();
                openDates.set(List.of());
                closeHasLock.countDown();
                await(releaseClose);
            }));
            assertThat(closeHasLock.await(5, TimeUnit.SECONDS)).isTrue();

            var mutation = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                try {
                    if (settlement) service.validateSettlementMutationAllowed();
                    else service.validateNewOperationalMutationAllowed();
                    return null;
                } catch (ResourceConflictException exception) {
                    return exception.getMessage();
                } finally {
                    mutationFinished.countDown();
                }
            }));

            assertThat(mutationFinished.await(250, TimeUnit.MILLISECONDS)).isFalse();
            releaseClose.countDown();
            close.get(5, TimeUnit.SECONDS);
            assertThat(mutation.get(5, TimeUnit.SECONDS)).isNotNull();
            return mutation.get();
        } finally {
            releaseClose.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private BusinessDate openDate() {
        BusinessDate value = new BusinessDate();
        value.setBusinessDate(LocalDate.of(2026, 9, 8));
        value.setStatus("OPEN");
        return value;
    }

    private void inTransaction(Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
    }

    private void enter(AtomicInteger active, AtomicInteger maximum) {
        int current = active.incrementAndGet();
        maximum.accumulateAndGet(current, Math::max);
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrency test latch.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrency test interrupted.", exception);
        }
    }
}
