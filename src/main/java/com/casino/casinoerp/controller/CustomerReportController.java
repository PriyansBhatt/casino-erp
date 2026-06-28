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
@RequestMapping("/api/customer-report")
public class CustomerReportController {

    private final WalletTransactionService walletTransactionService;

    public CustomerReportController(WalletTransactionService walletTransactionService) {
        this.walletTransactionService = walletTransactionService;
    }

    @GetMapping("/{customerId}/business-date/{businessDate}")
    public Map<String, Object> getCustomerReportByBusinessDate(
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

        BigDecimal totalCashOut = transactions.stream()
                .filter(tx -> "CASH_OUT".equalsIgnoreCase(tx.getTransactionType()))
                .map(WalletTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netLoss = totalBuyIn.subtract(totalCashOut);

        String resultStatus;
        if (netLoss.compareTo(BigDecimal.ZERO) > 0) {
            resultStatus = "CUSTOMER_LOSS";
        } else if (netLoss.compareTo(BigDecimal.ZERO) < 0) {
            resultStatus = "CUSTOMER_WIN";
        } else {
            resultStatus = "BREAK_EVEN";
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("customerId", customerId);
        response.put("businessDate", businessDate);
        response.put("totalBuyIn", totalBuyIn);
        response.put("totalCashOut", totalCashOut);
        response.put("netLoss", netLoss);
        response.put("resultStatus", resultStatus);

        return response;
    }

    @GetMapping("/{customerId}")
    public Map<String, Object> getCustomerReport(@PathVariable UUID customerId) {

        List<WalletTransaction> transactions =
                walletTransactionService.getByCustomer(customerId);

        BigDecimal totalBuyIn = transactions.stream()
                .filter(tx -> "BUY_IN".equalsIgnoreCase(tx.getTransactionType()))
                .map(WalletTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCashOut = transactions.stream()
                .filter(tx -> "CASH_OUT".equalsIgnoreCase(tx.getTransactionType()))
                .map(WalletTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netLoss = totalBuyIn.subtract(totalCashOut);

        String resultStatus;
        if (netLoss.compareTo(BigDecimal.ZERO) > 0) {
            resultStatus = "CUSTOMER_LOSS";
        } else if (netLoss.compareTo(BigDecimal.ZERO) < 0) {
            resultStatus = "CUSTOMER_WIN";
        } else {
            resultStatus = "BREAK_EVEN";
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("customerId", customerId);
        response.put("totalBuyIn", totalBuyIn);
        response.put("totalCashOut", totalCashOut);
        response.put("netLoss", netLoss);
        response.put("resultStatus", resultStatus);

        return response;
    }
}