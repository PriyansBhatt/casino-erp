package com.casino.casinoerp;

import com.casino.casinoerp.entity.ChipCashOut;
import com.casino.casinoerp.entity.CustomerSession;
import com.casino.casinoerp.entity.User;
import com.casino.casinoerp.repository.ChipCashOutRepository;
import com.casino.casinoerp.repository.CustomerSessionRepository;
import com.casino.casinoerp.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class ChipCashOutPersistenceTests {
    @Autowired ChipCashOutRepository repository;
    @Autowired CustomerSessionRepository sessionRepository;
    @Autowired UserRepository userRepository;

    @Test
    void generatedUuidPersistsAndCashOutReloadsBySession() {
        CustomerSession session = sessionRepository.findAll().stream()
                .filter(value -> value.getBusinessDate() != null)
                .findFirst().orElseThrow();
        User actor = userRepository.findAll().stream().findFirst().orElseThrow();

        ChipCashOut value = new ChipCashOut();
        value.setCashOutCode("CO-PERSISTENCE-" + java.util.UUID.randomUUID());
        value.setCustomerId(session.getCustomerId());
        value.setCustomerSessionId(session.getId());
        value.setCashPaid(new BigDecimal("100"));
        value.setTotalChipValueReturned(new BigDecimal("100"));
        value.setPaymentMode("CASH");
        value.setSameCustomerVerified(true);
        value.setThirdPartyAttempt(false);
        value.setCreatedBy(actor.getId());
        value.setBusinessDate(session.getBusinessDate());
        value.setCreatedAt(LocalDateTime.now());
        value.setIdempotencyKey("cashout-persistence-" + java.util.UUID.randomUUID());

        ChipCashOut saved = repository.saveAndFlush(value);

        assertThat(saved.getId()).isNotNull();
        assertThat(repository.findById(saved.getId())).isPresent();
        assertThat(repository.findByCustomerSessionId(session.getId()))
                .extracting(ChipCashOut::getId).contains(saved.getId());
    }
}
