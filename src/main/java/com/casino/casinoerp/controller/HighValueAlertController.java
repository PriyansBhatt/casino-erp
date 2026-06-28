package com.casino.casinoerp.controller;

import com.casino.casinoerp.entity.WalletTransaction;
import com.casino.casinoerp.service.WalletTransactionService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/high-value-alerts")
public class HighValueAlertController {

    private final WalletTransactionService walletTransactionService;

    public HighValueAlertController(WalletTransactionService walletTransactionService) {
        this.walletTransactionService = walletTransactionService;
    }

    @GetMapping("/{businessDate}")
    public Map<String, Object> getHighValueAlerts(@PathVariable LocalDate businessDate) {

        BigDecimal threshold = new BigDecimal("100000");

        List<WalletTransaction> alerts =
                walletTransactionService.getByBusinessDate(businessDate)
                        .stream()
                        .filter(tx -> tx.getAmount().compareTo(threshold) >= 0)
                        .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("businessDate", businessDate);
        response.put("threshold", threshold);
        response.put("alertCount", alerts.size());
        response.put("alerts", alerts);

        return response;
    }
}