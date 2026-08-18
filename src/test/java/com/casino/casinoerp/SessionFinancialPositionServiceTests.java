package com.casino.casinoerp;

import com.casino.casinoerp.dto.SessionFinancialPositionResponse;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.repository.*;
import com.casino.casinoerp.service.SessionFinancialPositionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SessionFinancialPositionServiceTests {
    private final CustomerSessionRepository sessionRepository = mock(CustomerSessionRepository.class);
    private final ChipBuyInRepository buyInRepository = mock(ChipBuyInRepository.class);
    private final ChipCashOutRepository cashOutRepository = mock(ChipCashOutRepository.class);
    private final VerifiedGamingResultRepository gamingRepository = mock(VerifiedGamingResultRepository.class);
    private final SessionFinancialPositionService service = new SessionFinancialPositionService(
            sessionRepository, buyInRepository, cashOutRepository, gamingRepository);
    private final UUID customerId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();

    @BeforeEach void setUp() {
        CustomerSession session = new CustomerSession(); session.setId(sessionId); session.setCustomerId(customerId);
        session.setBusinessDate(LocalDate.of(2026, 8, 8));
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(buyInRepository.findByCustomerSessionId(sessionId)).thenReturn(List.of());
        when(cashOutRepository.findByCustomerSessionId(sessionId)).thenReturn(List.of());
        when(gamingRepository.findByCustomerSessionIdOrderByCreatedAtAsc(sessionId)).thenReturn(List.of());
    }

    @Test void noRecordsReturnsZerosAndDoesNotCrash() {
        assertPosition(service.getPosition(sessionId), "0", "0", "0", "0", "0");
    }

    @Test void buyInOnly() {
        when(buyInRepository.findByCustomerSessionId(sessionId)).thenReturn(List.of(buyIn("100")));
        assertPosition(service.getPosition(sessionId), "100", "0", "0", "0", "100");
    }

    @Test void buyInAndVerifiedWin() {
        when(buyInRepository.findByCustomerSessionId(sessionId)).thenReturn(List.of(buyIn("100")));
        when(gamingRepository.findByCustomerSessionIdOrderByCreatedAtAsc(sessionId))
                .thenReturn(List.of(result(VerifiedGamingResultType.WIN, "50")));
        assertPosition(service.getPosition(sessionId), "100", "0", "50", "0", "150");
    }

    @Test void buyInAndVerifiedLoss() {
        when(buyInRepository.findByCustomerSessionId(sessionId)).thenReturn(List.of(buyIn("100")));
        when(gamingRepository.findByCustomerSessionIdOrderByCreatedAtAsc(sessionId))
                .thenReturn(List.of(result(VerifiedGamingResultType.LOSS, "30")));
        assertPosition(service.getPosition(sessionId), "100", "0", "0", "30", "70");
    }

    @Test void multipleRecordsWithWinAndCashOut() {
        when(buyInRepository.findByCustomerSessionId(sessionId)).thenReturn(List.of(buyIn("100"), buyIn("25")));
        when(cashOutRepository.findByCustomerSessionId(sessionId)).thenReturn(List.of(cashOut("60")));
        when(gamingRepository.findByCustomerSessionIdOrderByCreatedAtAsc(sessionId)).thenReturn(List.of(
                result(VerifiedGamingResultType.WIN, "50"), result(VerifiedGamingResultType.WIN, "10"),
                result(VerifiedGamingResultType.LOSS, "5")));
        assertPosition(service.getPosition(sessionId), "125", "60", "60", "5", "120");
        verify(buyInRepository).findByCustomerSessionId(sessionId);
        verify(cashOutRepository).findByCustomerSessionId(sessionId);
        verify(gamingRepository).findByCustomerSessionIdOrderByCreatedAtAsc(sessionId);
    }

    private ChipBuyIn buyIn(String amount) {
        ChipBuyIn value = new ChipBuyIn(); value.setTotalChipValueIssued(new BigDecimal(amount)); return value;
    }
    private ChipCashOut cashOut(String amount) {
        ChipCashOut value = new ChipCashOut(); value.setTotalChipValueReturned(new BigDecimal(amount)); return value;
    }
    private VerifiedGamingResult result(VerifiedGamingResultType type, String amount) {
        VerifiedGamingResult value = new VerifiedGamingResult(); value.setResultType(type);
        value.setAmount(new BigDecimal(amount)); return value;
    }
    private void assertPosition(SessionFinancialPositionResponse value, String buyIn, String cashOut,
                                String win, String loss, String position) {
        assertThat(value.customerId()).isEqualTo(customerId);
        assertThat(value.totalBuyIn()).isEqualByComparingTo(buyIn);
        assertThat(value.totalCashOut()).isEqualByComparingTo(cashOut);
        assertThat(value.verifiedGamingWin()).isEqualByComparingTo(win);
        assertThat(value.verifiedGamingLoss()).isEqualByComparingTo(loss);
        assertThat(value.calculatedChipPosition()).isEqualByComparingTo(position);
    }
}
