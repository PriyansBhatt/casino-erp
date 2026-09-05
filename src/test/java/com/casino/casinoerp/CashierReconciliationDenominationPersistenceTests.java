package com.casino.casinoerp;

import com.casino.casinoerp.entity.CashierReconciliation;
import com.casino.casinoerp.entity.User;
import com.casino.casinoerp.repository.CashierReconciliationRepository;
import com.casino.casinoerp.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class CashierReconciliationDenominationPersistenceTests {
    @Autowired CashierReconciliationRepository repository;
    @Autowired UserRepository users;
    @Autowired EntityManager entityManager;

    @Test
    void newAndReopenedSubmissionsPersistAndReplaceManagedDenominations() {
        User cashier = users.findAll().stream().findFirst().orElseThrow();
        LocalDate isolatedDate = LocalDate.of(2099, 12, 31);

        CashierReconciliation reconciliation = new CashierReconciliation();
        reconciliation.setBusinessDate(isolatedDate);
        reconciliation.setCashierUserId(cashier.getId());
        reconciliation.setOpeningCash(new BigDecimal("100"));
        reconciliation.setExpectedClosingCash(new BigDecimal("100"));
        reconciliation.setActualClosingCash(new BigDecimal("100"));
        reconciliation.setVariance(BigDecimal.ZERO);
        reconciliation.setStatus("BALANCED");
        reconciliation.setLifecycleStatus("SUBMITTED");
        reconciliation.setIdempotencyKey("reconciliation-persistence-" + UUID.randomUUID());
        reconciliation.setSubmittedAt(LocalDateTime.now());
        reconciliation.getDenominations().put(100, 1);

        UUID reconciliationId = repository.saveAndFlush(reconciliation).getId();
        entityManager.clear();

        CashierReconciliation reopened = repository.findById(reconciliationId).orElseThrow();
        reopened.setLifecycleStatus("REOPENED");
        reopened.getDenominations().clear();
        reopened.getDenominations().put(50, 2);
        reopened.setIdempotencyKey("reconciliation-resubmission-" + UUID.randomUUID());
        reopened.setSubmittedAt(LocalDateTime.now());
        repository.saveAndFlush(reopened);
        entityManager.clear();

        CashierReconciliation resubmitted = repository.findById(reconciliationId).orElseThrow();
        assertThat(resubmitted.getId()).isEqualTo(reconciliationId);
        assertThat(resubmitted.getDenominations()).containsExactlyEntriesOf(Map.of(50, 2));
        assertThat(repository.findByCashierUserIdAndBusinessDate(cashier.getId(), isolatedDate))
                .get().extracting(CashierReconciliation::getId).isEqualTo(reconciliationId);
    }
}
