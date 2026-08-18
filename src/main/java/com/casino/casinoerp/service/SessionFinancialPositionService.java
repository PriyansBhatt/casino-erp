package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.SessionFinancialPositionResponse;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.exception.ResourceNotFoundException;
import com.casino.casinoerp.repository.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.time.LocalDate;

@Service
public class SessionFinancialPositionService {

    private final CustomerSessionRepository sessionRepository;
    private final ChipBuyInRepository buyInRepository;
    private final ChipCashOutRepository cashOutRepository;
    private final VerifiedGamingResultRepository gamingResultRepository;

    public SessionFinancialPositionService(
            CustomerSessionRepository sessionRepository,
            ChipBuyInRepository buyInRepository,
            ChipCashOutRepository cashOutRepository,
            VerifiedGamingResultRepository gamingResultRepository) {
        this.sessionRepository = sessionRepository;
        this.buyInRepository = buyInRepository;
        this.cashOutRepository = cashOutRepository;
        this.gamingResultRepository = gamingResultRepository;
    }

    public SessionFinancialPositionResponse getPosition(UUID sessionId) {
        CustomerSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer session not found."));

        BigDecimal totalBuyIn = buyInRepository.findByCustomerSessionId(sessionId).stream()
                .map(ChipBuyIn::getTotalChipValueIssued)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCashOut = cashOutRepository.findByCustomerSessionId(sessionId).stream()
                .map(ChipCashOut::getTotalChipValueReturned)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<VerifiedGamingResult> gamingResults =
                gamingResultRepository.findByCustomerSessionIdOrderByCreatedAtAsc(sessionId);
        BigDecimal verifiedWin = gamingResults.stream()
                .filter(result -> result.getResultType() == VerifiedGamingResultType.WIN)
                .map(VerifiedGamingResult::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal verifiedLoss = gamingResults.stream()
                .filter(result -> result.getResultType() == VerifiedGamingResultType.LOSS)
                .map(VerifiedGamingResult::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal calculatedChipPosition = totalBuyIn
                .add(verifiedWin)
                .subtract(verifiedLoss)
                .subtract(totalCashOut);

        return new SessionFinancialPositionResponse(
                session.getCustomerId(), session.getId(), session.getBusinessDate(),
                totalBuyIn, totalCashOut, verifiedWin, verifiedLoss, calculatedChipPosition
        );
    }

    public BigDecimal getOutstandingPosition(LocalDate businessDate) {
        BigDecimal buyIns = buyInRepository.findByBusinessDate(businessDate).stream()
                .map(ChipBuyIn::getTotalChipValueIssued).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cashOuts = cashOutRepository.findByBusinessDate(businessDate).stream()
                .map(ChipCashOut::getTotalChipValueReturned).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<VerifiedGamingResult> results = gamingResultRepository.findByBusinessDate(businessDate);
        BigDecimal wins = results.stream().filter(value -> value.getResultType() == VerifiedGamingResultType.WIN)
                .map(VerifiedGamingResult::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal losses = results.stream().filter(value -> value.getResultType() == VerifiedGamingResultType.LOSS)
                .map(VerifiedGamingResult::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return buyIns.add(wins).subtract(losses).subtract(cashOuts);
    }
}
