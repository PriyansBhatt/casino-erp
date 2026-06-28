package com.casino.casinoerp.controller;

import com.casino.casinoerp.entity.WalletTransaction;
import com.casino.casinoerp.service.WalletTransactionService;
import org.springframework.web.bind.annotation.*;
import com.casino.casinoerp.dto.ApiResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/casino-dashboard")
public class CasinoDashboardController {

    private final WalletTransactionService walletTransactionService;

    public CasinoDashboardController(
            WalletTransactionService walletTransactionService
    ) {
        this.walletTransactionService = walletTransactionService;
    }

    @GetMapping("/{businessDate}")
    public ApiResponse<Map<String, Object>> getDashboard(
            @PathVariable LocalDate businessDate
    ) {

        List<WalletTransaction> transactions =
                walletTransactionService.getByBusinessDate(businessDate);

        BigDecimal totalBuyIn = transactions.stream()
                .filter(tx -> "BUY_IN".equalsIgnoreCase(tx.getTransactionType()))
                .map(WalletTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCashOut = transactions.stream()
                .filter(tx -> "CASH_OUT".equalsIgnoreCase(tx.getTransactionType()))
                .map(WalletTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal casinoNet = totalBuyIn.subtract(totalCashOut);

        long activeCustomers = transactions.stream()
                .map(WalletTransaction::getCustomerId)
                .distinct()
                .count();

        long highValueAlerts = transactions.stream()
                .filter(tx ->
                        tx.getAmount().compareTo(new BigDecimal("100000")) >= 0)
                .count();

        Map<UUID, Long> customerCounts = new HashMap<>();

        for (WalletTransaction tx : transactions) {
            customerCounts.put(
                    tx.getCustomerId(),
                    customerCounts.getOrDefault(
                            tx.getCustomerId(), 0L
                    ) + 1
            );
        }

        long suspiciousAlerts = customerCounts.values()
                .stream()
                .filter(count -> count >= 5)
                .count();

        Map<String, Object> response = new LinkedHashMap<>();

        response.put("businessDate", businessDate);
        response.put("totalBuyIn", totalBuyIn);
        response.put("totalCashOut", totalCashOut);
        response.put("casinoNet", casinoNet);
        response.put("activeCustomers", activeCustomers);
        response.put("highValueAlerts", highValueAlerts);
        response.put("suspiciousAlerts", suspiciousAlerts);

        return ApiResponse.success(
                "Casino dashboard loaded successfully",
                response
        );
    }
}