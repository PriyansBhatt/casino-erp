package com.casino.casinoerp;

import com.casino.casinoerp.repository.BusinessDateRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

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
