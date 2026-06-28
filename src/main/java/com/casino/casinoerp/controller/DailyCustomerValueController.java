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
@RequestMapping("/api/daily-customer-value")
public class DailyCustomerValueController {

    private final WalletTransactionService walletTransactionService;
    private final CustomerServiceRecordService serviceRecordService;

    public DailyCustomerValueController(
            WalletTransactionService walletTransactionService,
            CustomerServiceRecordService serviceRecordService) {
        this.walletTransactionService = walletTransactionService;
        this.serviceRecordService = serviceRecordService;
    }

    @GetMapping("/{businessDate}")
    public Map<String, Object> getDailyCustomerValue(@PathVariable LocalDate businessDate) {

        List<WalletTransaction> transactions =
                walletTransactionService.getByBusinessDate(businessDate);

        List<CustomerServiceRecord> services =
                serviceRecordService.getByBusinessDate(businessDate);

        Set<UUID> customerIds = new HashSet<>();

        transactions.forEach(tx -> customerIds.add(tx.getCustomerId()));
        services.forEach(service -> customerIds.add(service.getCustomerId()));

        List<Map<String, Object>> customerReports = new ArrayList<>();

        for (UUID customerId : customerIds) {

            BigDecimal totalBuyIn = transactions.stream()
                    .filter(tx -> customerId.equals(tx.getCustomerId()))
                    .filter(tx -> "BUY_IN".equalsIgnoreCase(tx.getTransactionType()))
                    .map(WalletTransaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalCashOut = transactions.stream()
                    .filter(tx -> customerId.equals(tx.getCustomerId()))
                    .filter(tx -> "CASH_OUT".equalsIgnoreCase(tx.getTransactionType()))
                    .map(WalletTransaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal customerNetLoss = totalBuyIn.subtract(totalCashOut);

            BigDecimal totalServiceCost = services.stream()
                    .filter(service -> customerId.equals(service.getCustomerId()))
                    .map(service -> service.getServiceCost() == null
                            ? BigDecimal.ZERO
                            : service.getServiceCost())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal netCustomerValue =
                    customerNetLoss.subtract(totalServiceCost);

            String valueStatus;

            if (netCustomerValue.compareTo(BigDecimal.ZERO) > 0) {
                valueStatus = "PROFITABLE_CUSTOMER";
            } else if (netCustomerValue.compareTo(BigDecimal.ZERO) < 0) {
                valueStatus = "LOSS_CUSTOMER";
            } else {
                valueStatus = "BREAK_EVEN";
            }

            Map<String, Object> report = new LinkedHashMap<>();
            report.put("customerId", customerId);
            report.put("totalBuyIn", totalBuyIn);
            report.put("totalCashOut", totalCashOut);
            report.put("customerNetLoss", customerNetLoss);
            report.put("totalServiceCost", totalServiceCost);
            report.put("netCustomerValue", netCustomerValue);
            report.put("valueStatus", valueStatus);

            customerReports.add(report);
        }

        BigDecimal totalNetCustomerValue = customerReports.stream()
                .map(report -> (BigDecimal) report.get("netCustomerValue"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long profitableCustomers = customerReports.stream()
                .filter(report -> "PROFITABLE_CUSTOMER".equals(report.get("valueStatus")))
                .count();

        long lossCustomers = customerReports.stream()
                .filter(report -> "LOSS_CUSTOMER".equals(report.get("valueStatus")))
                .count();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("businessDate", businessDate);
        response.put("customerCount", customerReports.size());
        response.put("customers", customerReports);
        response.put("totalNetCustomerValue", totalNetCustomerValue);
        response.put("profitableCustomers", profitableCustomers);
        response.put("lossCustomers", lossCustomers);

        return response;
    }
}