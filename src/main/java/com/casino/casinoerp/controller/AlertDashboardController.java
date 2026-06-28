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
@RequestMapping("/api/alert-dashboard")
public class AlertDashboardController {

    private final WalletTransactionService walletTransactionService;

    public AlertDashboardController(WalletTransactionService walletTransactionService) {
        this.walletTransactionService = walletTransactionService;
    }

    @GetMapping("/{businessDate}")
    public Map<String, Object> getAlertDashboard(@PathVariable LocalDate businessDate) {

        List<WalletTransaction> transactions =
                walletTransactionService.getByBusinessDate(businessDate);

        BigDecimal highValueThreshold = new BigDecimal("100000");

        long highValueAlertCount = transactions.stream()
                .filter(tx -> tx.getAmount().compareTo(highValueThreshold) >= 0)
                .count();

        Map<UUID, Long> customerTransactionCount = new LinkedHashMap<>();

        for (WalletTransaction tx : transactions) {
            customerTransactionCount.put(
                    tx.getCustomerId(),
                    customerTransactionCount.getOrDefault(tx.getCustomerId(), 0L) + 1
            );
        }

        long suspiciousAlertCount = customerTransactionCount.values()
                .stream()
                .filter(count -> count >= 5)
                .count();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("businessDate", businessDate);
        response.put("highValueAlertCount", highValueAlertCount);
        response.put("suspiciousAlertCount", suspiciousAlertCount);
        response.put("totalAlertCount", highValueAlertCount + suspiciousAlertCount);

        return response;
    }
}