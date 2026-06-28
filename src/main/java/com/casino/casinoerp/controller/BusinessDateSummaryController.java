package com.casino.casinoerp.controller;

import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.service.*;
import org.springframework.web.bind.annotation.*;
import com.casino.casinoerp.dto.ApiResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/business-date-summary")
public class BusinessDateSummaryController {

    private final WalletTransactionService walletTransactionService;
    private final CustomerServiceRecordService serviceRecordService;
    private final CustomerSessionService customerSessionService;
    private final BusinessDateService businessDateService;

    public BusinessDateSummaryController(
            WalletTransactionService walletTransactionService,
            CustomerServiceRecordService serviceRecordService,
            CustomerSessionService customerSessionService,
            BusinessDateService businessDateService) {

        this.walletTransactionService = walletTransactionService;
        this.serviceRecordService = serviceRecordService;
        this.customerSessionService = customerSessionService;
        this.businessDateService = businessDateService;
    }

    @GetMapping("/{businessDate}")
    public ApiResponse<Map<String, Object>> getSummary(@PathVariable LocalDate businessDate) {
        List<WalletTransaction> transactions =
                walletTransactionService.getByBusinessDate(businessDate);

        List<CustomerServiceRecord> services =
                serviceRecordService.getByBusinessDate(businessDate);

        List<CustomerSession> sessions =
                customerSessionService.getAllSessions()
                        .stream()
                        .filter(s -> businessDate.equals(s.getBusinessDate()))
                        .toList();

        BigDecimal totalBuyIn = transactions.stream()
                .filter(t -> "BUY_IN".equalsIgnoreCase(t.getTransactionType()))
                .map(WalletTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCashOut = transactions.stream()
                .filter(t -> "CASH_OUT".equalsIgnoreCase(t.getTransactionType()))
                .map(WalletTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalServiceCost = services.stream()
                .map(s -> s.getServiceCost() == null ? BigDecimal.ZERO : s.getServiceCost())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal casinoNet = totalBuyIn.subtract(totalCashOut);

        Set<UUID> customerIds = new HashSet<>();
        transactions.forEach(t -> customerIds.add(t.getCustomerId()));
        services.forEach(s -> customerIds.add(s.getCustomerId()));
        sessions.forEach(s -> customerIds.add(s.getCustomerId()));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("businessDate", businessDate);
        response.put("totalBuyIn", totalBuyIn);
        response.put("totalCashOut", totalCashOut);
        response.put("casinoNet", casinoNet);
        response.put("totalServiceCost", totalServiceCost);
        response.put("netAfterServiceCost", casinoNet.subtract(totalServiceCost));
        response.put("customerCount", customerIds.size());
        response.put("sessionCount", sessions.size());
        response.put("businessDateStatus",
                businessDateService.getCurrentOpenBusinessDate()
                        .map(bd -> bd.getBusinessDate().equals(businessDate) ? "OPEN" : "CLOSED")
                        .orElse("CLOSED")
        );

        return ApiResponse.success(
                "Business date summary loaded successfully",
                response
        );
    }
}