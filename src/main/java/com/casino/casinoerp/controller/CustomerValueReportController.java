package com.casino.casinoerp.controller;

import com.casino.casinoerp.entity.CustomerServiceRecord;
import com.casino.casinoerp.entity.WalletTransaction;
import com.casino.casinoerp.service.CustomerServiceRecordService;
import com.casino.casinoerp.service.WalletTransactionService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/customer-value-report")
public class CustomerValueReportController {

    private final WalletTransactionService walletTransactionService;
    private final CustomerServiceRecordService serviceRecordService;

    public CustomerValueReportController(
            WalletTransactionService walletTransactionService,
            CustomerServiceRecordService serviceRecordService) {
        this.walletTransactionService = walletTransactionService;
        this.serviceRecordService = serviceRecordService;
    }

    @GetMapping("/customer/{customerId}/business-date/{businessDate}")
    public Map<String, Object> getCustomerValueReport(
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

        BigDecimal customerNetLoss = totalBuyIn.subtract(totalCashOut);

        List<CustomerServiceRecord> services =
                serviceRecordService.getByCustomer(customerId)
                        .stream()
                        .filter(s -> businessDate.equals(s.getBusinessDate()))
                        .toList();

        BigDecimal totalServiceCost = services.stream()
                .map(s -> s.getServiceCost() == null ? BigDecimal.ZERO : s.getServiceCost())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netCustomerValue = customerNetLoss.subtract(totalServiceCost);

        String valueStatus;

        if (netCustomerValue.compareTo(BigDecimal.ZERO) > 0) {
            valueStatus = "PROFITABLE_CUSTOMER";
        } else if (netCustomerValue.compareTo(BigDecimal.ZERO) < 0) {
            valueStatus = "LOSS_CUSTOMER";
        } else {
            valueStatus = "BREAK_EVEN";
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("customerId", customerId);
        response.put("businessDate", businessDate);
        response.put("totalBuyIn", totalBuyIn);
        response.put("totalCashOut", totalCashOut);
        response.put("customerNetLoss", customerNetLoss);
        response.put("totalServiceCost", totalServiceCost);
        response.put("netCustomerValue", netCustomerValue);
        response.put("valueStatus", valueStatus);

        return response;
    }
}