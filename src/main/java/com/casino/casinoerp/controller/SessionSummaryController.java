package com.casino.casinoerp.controller;

import com.casino.casinoerp.entity.ChipBuyIn;
import com.casino.casinoerp.entity.ChipCashOut;
import com.casino.casinoerp.service.ChipBuyInService;
import com.casino.casinoerp.service.ChipCashOutService;
import com.casino.casinoerp.service.WalletTransactionService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/session-summary")
public class SessionSummaryController {

    private final ChipBuyInService buyInService;
    private final ChipCashOutService cashOutService;
    private final WalletTransactionService walletTransactionService;

    public SessionSummaryController(ChipBuyInService buyInService,
                                    WalletTransactionService walletTransactionService,
                                    ChipCashOutService cashOutService) {
        this.buyInService = buyInService;
        this.cashOutService = cashOutService;
        this.walletTransactionService = walletTransactionService;
    }

    @GetMapping("/{customerSessionId}")
    public Map<String, Object> getSessionSummary(@PathVariable UUID customerSessionId) {

        List<ChipBuyIn> buyIns = buyInService.getBySessionId(customerSessionId);
        List<ChipCashOut> cashOuts = cashOutService.getBySessionId(customerSessionId);

        BigDecimal totalBuyIn = buyIns.stream()
                .map(ChipBuyIn::getTotalChipValueIssued)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCashOut = cashOuts.stream()
                .map(ChipCashOut::getTotalChipValueReturned)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal customerNet = totalCashOut.subtract(totalBuyIn);
        BigDecimal casinoNet = totalBuyIn.subtract(totalCashOut);

        String resultStatus;

        if (customerNet.compareTo(BigDecimal.ZERO) > 0) {
            resultStatus = "CUSTOMER_WIN";
        } else if (customerNet.compareTo(BigDecimal.ZERO) < 0) {
            resultStatus = "CUSTOMER_LOSS";
        } else {
            resultStatus = "BREAK_EVEN";
        }

        BigDecimal walletBalance =
                walletTransactionService.getSessionBalance(
                        buyIns.get(0).getCustomerId(),
                        customerSessionId
                );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("resultStatus", resultStatus);
        response.put("customerSessionId", customerSessionId);
        response.put("totalBuyIn", totalBuyIn);
        response.put("totalCashOut", totalCashOut);
        response.put("customerNet", customerNet);
        response.put("casinoNet", casinoNet);
        response.put("walletBalance", walletBalance);

        return response;
    }
}