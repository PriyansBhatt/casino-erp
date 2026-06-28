package com.casino.casinoerp.controller;

import com.casino.casinoerp.entity.WalletTransaction;
import com.casino.casinoerp.service.WalletTransactionService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/losing-return")
public class LosingReturnController {

    private final WalletTransactionService walletTransactionService;

    public LosingReturnController(WalletTransactionService walletTransactionService) {
        this.walletTransactionService = walletTransactionService;
    }

    @GetMapping("/customer/{customerId}/business-date/{businessDate}")
    public Map<String, Object> checkEligibility(
            @PathVariable UUID customerId,
            @PathVariable LocalDate businessDate) {

        List<WalletTransaction> transactions =
                walletTransactionService.getByCustomer(customerId)
                        .stream()
                        .filter(tx -> businessDate.equals(tx.getBusinessDate()))
                        .toList();

        BigDecimal totalBuyIn = transactions.stream()
                .filter(tx -> "BUY_IN".equalsIgnoreCase(tx.getTransactionType()))
                .map(WalletTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCashOut = transactions.stream()
                .filter(tx -> "CASH_OUT".equalsIgnoreCase(tx.getTransactionType()))
                .map(WalletTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netLoss = totalBuyIn.subtract(totalCashOut);

        boolean eligible = netLoss.compareTo(BigDecimal.ZERO) > 0;

        BigDecimal returnRate = new BigDecimal("0.10");
        BigDecimal estimatedReturn = eligible
                ? netLoss.multiply(returnRate)
                : BigDecimal.ZERO;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("customerId", customerId);
        response.put("businessDate", businessDate);
        response.put("totalBuyIn", totalBuyIn);
        response.put("totalCashOut", totalCashOut);
        response.put("netVerifiedLoss", netLoss);
        response.put("eligible", eligible);
        response.put("returnRate", returnRate);
        response.put("estimatedReturn", estimatedReturn);
        response.put("rule", "Losing return is calculated on net verified customer loss, not gross buy-in.");

        return response;
    }
}