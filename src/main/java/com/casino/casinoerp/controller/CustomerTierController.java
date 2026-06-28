package com.casino.casinoerp.controller;

import com.casino.casinoerp.entity.WalletTransaction;
import com.casino.casinoerp.service.WalletTransactionService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/customer-tier")
public class CustomerTierController {

    private final WalletTransactionService walletTransactionService;

    public CustomerTierController(WalletTransactionService walletTransactionService) {
        this.walletTransactionService = walletTransactionService;
    }

    @GetMapping("/{customerId}/business-date/{businessDate}")
    public Map<String, Object> getCustomerTierByBusinessDate(
            @PathVariable UUID customerId,
            @PathVariable java.time.LocalDate businessDate) {

        List<WalletTransaction> transactions =
                walletTransactionService.getByCustomer(customerId)
                        .stream()
                        .filter(tx -> businessDate.equals(tx.getBusinessDate()))
                        .toList();

        BigDecimal totalBuyIn = transactions.stream()
                .filter(tx -> "BUY_IN".equalsIgnoreCase(tx.getTransactionType()))
                .map(WalletTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String tier;

        if (totalBuyIn.compareTo(new BigDecimal("1000000")) >= 0) {
            tier = "PLATINUM";
        } else if (totalBuyIn.compareTo(new BigDecimal("500000")) >= 0) {
            tier = "GOLD";
        } else if (totalBuyIn.compareTo(new BigDecimal("100000")) >= 0) {
            tier = "SILVER";
        } else {
            tier = "REGULAR";
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("customerId", customerId);
        response.put("businessDate", businessDate);
        response.put("totalBuyIn", totalBuyIn);
        response.put("tier", tier);

        return response;
    }

    @GetMapping("/{customerId}")
    public Map<String, Object> getCustomerTier(@PathVariable UUID customerId) {

        List<WalletTransaction> transactions =
                walletTransactionService.getByCustomer(customerId);

        BigDecimal totalBuyIn = transactions.stream()
                .filter(tx -> "BUY_IN".equalsIgnoreCase(tx.getTransactionType()))
                .map(WalletTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String tier;

        if (totalBuyIn.compareTo(new BigDecimal("1000000")) >= 0) {
            tier = "PLATINUM";
        } else if (totalBuyIn.compareTo(new BigDecimal("500000")) >= 0) {
            tier = "GOLD";
        } else if (totalBuyIn.compareTo(new BigDecimal("100000")) >= 0) {
            tier = "SILVER";
        } else {
            tier = "REGULAR";
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("customerId", customerId);
        response.put("totalBuyIn", totalBuyIn);
        response.put("tier", tier);

        return response;
    }
}