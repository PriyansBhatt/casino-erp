package com.casino.casinoerp;

import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class VerifiedGamingResultPersistenceTests {
    @Autowired VerifiedGamingResultRepository repository;
    @Autowired CustomerSessionRepository sessionRepository;
    @Autowired UserRepository userRepository;
    @Autowired EntityManager entityManager;

    @Test
    void persistsVerifiedTableResultWithGeneratedUuid() {
        CustomerSession session = sessionRepository.findAll().stream()
                .filter(value -> value.getBusinessDate() != null)
                .findFirst()
                .orElseThrow();
        User actor = userRepository.findAll().stream().findFirst().orElseThrow();

        VerifiedGamingResult result = new VerifiedGamingResult();
        result.setCustomerId(session.getCustomerId());
        result.setCustomerSessionId(session.getId());
        result.setBusinessDate(session.getBusinessDate());
        result.setSourceType(VerifiedGamingSourceType.TABLE);
        result.setResultType(VerifiedGamingResultType.WIN);
        result.setAmount(new BigDecimal("1000"));
        result.setDenominations(Map.of(500, 2));
        result.setIdempotencyKey("persistence-denominations-" + java.util.UUID.randomUUID());
        result.setCreatedAt(LocalDateTime.now());
        result.setCreatedBy(actor.getId());

        VerifiedGamingResult saved = repository.saveAndFlush(result);

        assertThat(saved.getId()).isNotNull();
        entityManager.clear();
        VerifiedGamingResult reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getDenominations()).containsExactlyEntriesOf(Map.of(500, 2));
        assertThat(reloaded.getAmount()).isEqualByComparingTo("1000");
    }
}
