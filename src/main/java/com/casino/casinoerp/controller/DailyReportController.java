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
@RequestMapping("/api/daily-report")
public class DailyReportController {

    private final WalletTransactionService walletTransactionService;

    public DailyReportController(WalletTransactionService walletTransactionService) {
        this.walletTransactionService = walletTransactionService;
    }

    @GetMapping("/{businessDate}/customers")
    public Map<String, Object> getCustomerReport(@PathVariable LocalDate businessDate) {

        List<WalletTransaction> transactions =
                walletTransactionService.getByBusinessDate(businessDate);

        Map<Object, BigDecimal> customerNetMap = new LinkedHashMap<>();

        for (WalletTransaction tx : transactions) {
            Object customerId = tx.getCustomerId();

            BigDecimal amount = "BUY_IN".equalsIgnoreCase(tx.getTransactionType())
                    ? tx.getAmount()
                    : tx.getAmount().negate();

            customerNetMap.put(
                    customerId,
                    customerNetMap.getOrDefault(customerId, BigDecimal.ZERO).add(amount)
            );
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("businessDate", businessDate);
        response.put("customers", customerNetMap);

        return response;
    }

    @GetMapping("/{businessDate}")
    public Map<String, Object> getDailyReport(@PathVariable LocalDate businessDate) {

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

        String casinoStatus;

        if (casinoNet.compareTo(BigDecimal.ZERO) > 0) {
            casinoStatus = "CASINO_WIN";
        } else if (casinoNet.compareTo(BigDecimal.ZERO) < 0) {
            casinoStatus = "CASINO_LOSS";
        } else {
            casinoStatus = "BREAK_EVEN";
        }

        long activeCustomers = transactions.stream()
                .map(WalletTransaction::getCustomerId)
                .distinct()
                .count();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("businessDate", businessDate);
        response.put("totalBuyIn", totalBuyIn);
        response.put("totalCashOut", totalCashOut);
        response.put("casinoNet", casinoNet);
        response.put("transactionCount", transactions.size());
        response.put("activeCustomers", activeCustomers);
        response.put("casinoStatus", casinoStatus);

        return response;
    }
}