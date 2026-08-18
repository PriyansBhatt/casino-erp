package com.casino.casinoerp;

import com.casino.casinoerp.entity.ChipBuyIn;
import com.casino.casinoerp.entity.CustomerSession;
import com.casino.casinoerp.repository.ChipBuyInRepository;
import com.casino.casinoerp.repository.CustomerSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class ChipBuyInPersistenceTests {

    @Autowired
    private ChipBuyInRepository buyInRepository;

    @Autowired
    private CustomerSessionRepository sessionRepository;

    @Test
    void insertsNewChipBuyInWithManuallyAssignedUuid() {
        CustomerSession session = sessionRepository.findAll().stream().findFirst().orElseThrow();
        UUID buyInId = UUID.randomUUID();

        ChipBuyIn buyIn = new ChipBuyIn();
        buyIn.setId(buyInId);
        buyIn.setBuyInCode("BI-PERSISTENCE-" + buyInId);
        buyIn.setCustomerSessionId(session.getId());
        buyIn.setCustomerId(session.getCustomerId());
        buyIn.setAmountReceived(new BigDecimal("1000"));
        buyIn.setPaymentMode("CASH");
        buyIn.setTotalChipValueIssued(new BigDecimal("1000"));
        buyIn.setBusinessDate(session.getBusinessDate());
        buyIn.setCreatedAt(LocalDateTime.now());
        buyIn.setIdempotencyKey("persistence-test-" + buyInId);

        ChipBuyIn saved = buyInRepository.saveAndFlush(buyIn);

        assertThat(saved.getId()).isEqualTo(buyInId);
        assertThat(saved.isNew()).isFalse();
        assertThat(buyInRepository.findById(buyInId)).isPresent();
    }
}
