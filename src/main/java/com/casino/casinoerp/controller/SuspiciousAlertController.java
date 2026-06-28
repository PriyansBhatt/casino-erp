package com.casino.casinoerp.controller;

import com.casino.casinoerp.entity.WalletTransaction;
import com.casino.casinoerp.service.WalletTransactionService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/suspicious-alerts")
public class SuspiciousAlertController {

    private final WalletTransactionService walletTransactionService;

    public SuspiciousAlertController(WalletTransactionService walletTransactionService) {
        this.walletTransactionService = walletTransactionService;
    }

    @GetMapping("/{businessDate}")
    public Map<String, Object> getSuspiciousAlerts(@PathVariable LocalDate businessDate) {

        List<WalletTransaction> transactions =
                walletTransactionService.getByBusinessDate(businessDate);

        Map<UUID, Long> customerTransactionCount = new LinkedHashMap<>();

        for (WalletTransaction tx : transactions) {
            UUID customerId = tx.getCustomerId();

            customerTransactionCount.put(
                    customerId,
                    customerTransactionCount.getOrDefault(customerId, 0L) + 1
            );
        }

        Map<UUID, Long> suspiciousCustomers = new LinkedHashMap<>();

        for (Map.Entry<UUID, Long> entry : customerTransactionCount.entrySet()) {
            if (entry.getValue() >= 5) {
                suspiciousCustomers.put(entry.getKey(), entry.getValue());
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("businessDate", businessDate);
        response.put("rule", "Customer has 5 or more wallet transactions in one business date");
        response.put("alertCount", suspiciousCustomers.size());
        response.put("alerts", suspiciousCustomers);

        return response;
    }
}