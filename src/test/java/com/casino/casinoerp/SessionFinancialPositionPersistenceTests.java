package com.casino.casinoerp;

import com.casino.casinoerp.dto.SessionFinancialPositionResponse;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.repository.*;
import com.casino.casinoerp.service.SessionFinancialPositionService;
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
class SessionFinancialPositionPersistenceTests {
    @Autowired SessionFinancialPositionService service;
    @Autowired CustomerSessionRepository sessionRepository;
    @Autowired UserRepository userRepository;
    @Autowired ChipBuyInRepository buyInRepository;
    @Autowired ChipCashOutRepository cashOutRepository;
    @Autowired VerifiedGamingResultRepository gamingResultRepository;

    @Test
    void persistedCashOutsReduceTheAuthoritativeSessionPosition() {
        User actor = userRepository.findAll().stream().findFirst().orElseThrow();
        CustomerSession source = sessionRepository.findAll().stream()
                .filter(value -> value.getBusinessDate() != null)
                .findFirst().orElseThrow();
        CustomerSession session = new CustomerSession();
        UUID sessionId = UUID.randomUUID();
        session.setId(sessionId);
        session.setSessionCode("SES-SUMMARY-" + sessionId);
        session.setCustomerId(source.getCustomerId());
        session.setSessionDate(source.getBusinessDate());
        session.setEntryTime(LocalDateTime.now());
        session.setStatus("OPEN");
        session.setOpenedBy(actor.getId());
        session.setBusinessDate(source.getBusinessDate());
        session.setCreatedAt(LocalDateTime.now());
        session = sessionRepository.saveAndFlush(session);

        buyInRepository.saveAndFlush(buyIn(session, actor, "250000"));
        gamingResultRepository.saveAndFlush(win(session, actor, "55000"));
        cashOutRepository.saveAndFlush(cashOut(session, actor, "5000"));

        SessionFinancialPositionResponse first = service.getPosition(session.getId());
        assertThat(first.totalCashOut()).isEqualByComparingTo("5000");
        assertThat(first.calculatedChipPosition()).isEqualByComparingTo("300000");

        cashOutRepository.saveAndFlush(cashOut(session, actor, "10000"));

        SessionFinancialPositionResponse second = service.getPosition(session.getId());
        assertThat(second.totalCashOut()).isEqualByComparingTo("15000");
        assertThat(second.calculatedChipPosition()).isEqualByComparingTo("290000");
    }

    private ChipBuyIn buyIn(CustomerSession session, User actor, String amount) {
        UUID id = UUID.randomUUID();
        ChipBuyIn value = new ChipBuyIn();
        value.setId(id);
        value.setBuyInCode("BI-SUMMARY-" + id);
        value.setCustomerId(session.getCustomerId());
        value.setCustomerSessionId(session.getId());
        value.setAmountReceived(new BigDecimal(amount));
        value.setTotalChipValueIssued(new BigDecimal(amount));
        value.setPaymentMode("CASH");
        value.setBusinessDate(session.getBusinessDate());
        value.setCreatedAt(LocalDateTime.now());
        value.setCreatedBy(actor.getId());
        value.setIdempotencyKey("summary-buyin-" + id);
        return value;
    }

    private VerifiedGamingResult win(CustomerSession session, User actor, String amount) {
        VerifiedGamingResult value = new VerifiedGamingResult();
        value.setCustomerId(session.getCustomerId());
        value.setCustomerSessionId(session.getId());
        value.setBusinessDate(session.getBusinessDate());
        value.setSourceType(VerifiedGamingSourceType.TABLE);
        value.setResultType(VerifiedGamingResultType.WIN);
        value.setAmount(new BigDecimal(amount));
        value.setDenominations(java.util.Map.of(25000, 2, 5000, 1));
        value.setIdempotencyKey("summary-result-" + UUID.randomUUID());
        value.setCreatedAt(LocalDateTime.now());
        value.setCreatedBy(actor.getId());
        return value;
    }

    private ChipCashOut cashOut(CustomerSession session, User actor, String amount) {
        ChipCashOut value = new ChipCashOut();
        value.setCashOutCode("CO-SUMMARY-" + UUID.randomUUID());
        value.setCustomerId(session.getCustomerId());
        value.setCustomerSessionId(session.getId());
        value.setCashPaid(new BigDecimal(amount));
        value.setTotalChipValueReturned(new BigDecimal(amount));
        value.setPaymentMode("CASH");
        value.setSameCustomerVerified(true);
        value.setThirdPartyAttempt(false);
        value.setBusinessDate(session.getBusinessDate());
        value.setCreatedAt(LocalDateTime.now());
        value.setCreatedBy(actor.getId());
        value.setIdempotencyKey("summary-cashout-" + UUID.randomUUID());
        return value;
    }
}
