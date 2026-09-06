package com.casino.casinoerp.controller;

import com.casino.casinoerp.repository.*;
import com.casino.casinoerp.service.PitTableReconciliationService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final CustomerRepository customerRepository;
    private final ChipBuyInRepository buyInRepository;
    private final ChipCashOutRepository cashOutRepository;
    private final CustomerSessionRepository sessionRepository;
    private final CustomerCheckInRepository checkInRepository;
    private final CustomerWalletRepository walletRepository;
    private final PitTableRepository pitTableRepository;
    private final PitTableTransactionRepository pitTableTransactionRepository;
    private final PitTableReconciliationService pitTableReconciliationService;

    public DashboardController(
            CustomerRepository customerRepository,
            ChipBuyInRepository buyInRepository,
            ChipCashOutRepository cashOutRepository,
            CustomerSessionRepository sessionRepository,
            CustomerCheckInRepository checkInRepository,
            CustomerWalletRepository walletRepository, PitTableRepository pitTableRepository,
            PitTableTransactionRepository pitTableTransactionRepository,
            PitTableReconciliationService pitTableReconciliationService) {

        this.customerRepository = customerRepository;
        this.buyInRepository = buyInRepository;
        this.cashOutRepository = cashOutRepository;
        this.sessionRepository = sessionRepository;
        this.checkInRepository = checkInRepository;
        this.walletRepository = walletRepository;
        this.pitTableRepository = pitTableRepository;
        this.pitTableTransactionRepository = pitTableTransactionRepository;
        this.pitTableReconciliationService = pitTableReconciliationService;
    }

    @GetMapping("/active-sessions")
    public Object getActiveSessions() {
        return sessionRepository.findAll().stream()
                .filter(s -> "active".equalsIgnoreCase(s.getStatus()))
                .toList();
    }

    @GetMapping("/casino-position")
    public Map<String, Object> getCasinoPosition() {

        BigDecimal totalBuyIn = buyInRepository.findAll().stream()
                .map(b -> b.getTotalChipValueIssued())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCashOut = cashOutRepository.findAll().stream()
                .map(c -> c.getTotalChipValueReturned())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal casinoNet = totalBuyIn.subtract(totalCashOut);

        String casinoStatus;

        if (casinoNet.compareTo(BigDecimal.ZERO) > 0) {
            casinoStatus = "PROFIT";
        } else if (casinoNet.compareTo(BigDecimal.ZERO) < 0) {
            casinoStatus = "LOSS";
        } else {
            casinoStatus = "BREAK_EVEN";
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalBuyIn", totalBuyIn);
        response.put("totalCashOut", totalCashOut);
        response.put("casinoNet", casinoNet);
        response.put("casinoStatus", casinoStatus);

        return response;
    }

    @GetMapping("/cashier-activity")
    public Map<String, Object> getCashierActivity() {

        Map<String, Object> response = new LinkedHashMap<>();

        response.put("buyIns", buyInRepository.findAll());
        response.put("cashOuts", cashOutRepository.findAll());
        response.put("wallets", walletRepository.findAll());
        response.put("totalBuyIns", buyInRepository.count());
        response.put("totalCashOuts", cashOutRepository.count());
        response.put("totalWalletRecords", walletRepository.count());

        return response;
    }

    @GetMapping("/high-value-alerts")
    public Object getHighValueAlerts() {
        return buyInRepository.findAll().stream()
                .filter(b -> Boolean.TRUE.equals(b.getHighValueAlert()))
                .toList();
    }

    @GetMapping("/pit-summary")
    public Map<String, Object> getPitSummary() {

        long totalTables = pitTableRepository.count();

        long openTables = pitTableRepository.findAll().stream()
                .filter(t -> "OPEN".equalsIgnoreCase(t.getStatus()))
                .count();

        long closedTables = pitTableRepository.findAll().stream()
                .filter(t -> "CLOSED".equalsIgnoreCase(t.getStatus()))
                .count();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalTables", totalTables);
        response.put("openTables", openTables);
        response.put("closedTables", closedTables);

        return response;
    }

    @GetMapping("/table-results")
    public Object getTableResults() {
        return pitTableRepository.findAll().stream()
                .filter(t -> "CLOSED".equalsIgnoreCase(t.getStatus()))
                .map(pitTableReconciliationService::reconcile)
                .toList();
    }

    @GetMapping("/pit-performance")
    public Map<String, Object> getPitPerformance() {

        BigDecimal totalTableWin = pitTableTransactionRepository.findAll().stream()
                .filter(t -> "TABLE_WIN".equalsIgnoreCase(t.getTransactionType()))
                .map(t -> t.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalTableLoss = pitTableTransactionRepository.findAll().stream()
                .filter(t -> "TABLE_LOSS".equalsIgnoreCase(t.getTransactionType()))
                .map(t -> t.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netPitResult = totalTableWin.subtract(totalTableLoss);

        String pitStatus;

        if (netPitResult.compareTo(BigDecimal.ZERO) > 0) {
            pitStatus = "PIT_PROFIT";
        } else if (netPitResult.compareTo(BigDecimal.ZERO) < 0) {
            pitStatus = "PIT_LOSS";
        } else {
            pitStatus = "BREAK_EVEN";
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalTableWin", totalTableWin);
        response.put("totalTableLoss", totalTableLoss);
        response.put("netPitResult", netPitResult);
        response.put("pitStatus", pitStatus);

        return response;
    }

    @GetMapping("/overall-position")
    public Map<String, Object> getOverallPosition() {

        BigDecimal totalBuyIn = buyInRepository.findAll().stream()
                .map(b -> b.getTotalChipValueIssued())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCashOut = cashOutRepository.findAll().stream()
                .map(c -> c.getTotalChipValueReturned())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal cashierNet = totalBuyIn.subtract(totalCashOut);

        BigDecimal totalTableWin = pitTableTransactionRepository.findAll().stream()
                .filter(t -> "TABLE_WIN".equalsIgnoreCase(t.getTransactionType()))
                .map(t -> t.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalTableLoss = pitTableTransactionRepository.findAll().stream()
                .filter(t -> "TABLE_LOSS".equalsIgnoreCase(t.getTransactionType()))
                .map(t -> t.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal pitNet = totalTableWin.subtract(totalTableLoss);
        BigDecimal overallPosition = cashierNet.add(pitNet);

        String status;

        if (overallPosition.compareTo(BigDecimal.ZERO) > 0) {
            status = "CASINO_PROFIT";
        } else if (overallPosition.compareTo(BigDecimal.ZERO) < 0) {
            status = "CASINO_LOSS";
        } else {
            status = "BREAK_EVEN";
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("cashierNet", cashierNet);
        response.put("pitNet", pitNet);
        response.put("overallPosition", overallPosition);
        response.put("status", status);

        return response;
    }
    
    @GetMapping("/summary")
    public Map<String, Object> getSummary() {

        long totalCustomers = customerRepository.count();
        long totalSessions = sessionRepository.count();
        long totalCheckIns = checkInRepository.count();
        long totalWalletRecords = walletRepository.count();

        BigDecimal totalBuyIn = buyInRepository.findAll().stream()
                .map(b -> b.getTotalChipValueIssued())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCashOut = cashOutRepository.findAll().stream()
                .map(c -> c.getTotalChipValueReturned())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long highValueBuyInAlerts = buyInRepository.findAll().stream()
                .filter(b -> Boolean.TRUE.equals(b.getHighValueAlert()))
                .count();

        BigDecimal casinoNet = totalBuyIn.subtract(totalCashOut);

        BigDecimal totalTableWin = pitTableTransactionRepository.findAll().stream()
                .filter(t -> "TABLE_WIN".equalsIgnoreCase(t.getTransactionType()))
                .map(t -> t.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalTableLoss = pitTableTransactionRepository.findAll().stream()
                .filter(t -> "TABLE_LOSS".equalsIgnoreCase(t.getTransactionType()))
                .map(t -> t.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netPitResult = totalTableWin.subtract(totalTableLoss);

        BigDecimal overallCasinoPosition = casinoNet.add(netPitResult);

        String overallCasinoStatus;

        if (overallCasinoPosition.compareTo(BigDecimal.ZERO) > 0) {
            overallCasinoStatus = "CASINO_PROFIT";
        } else if (overallCasinoPosition.compareTo(BigDecimal.ZERO) < 0) {
            overallCasinoStatus = "CASINO_LOSS";
        } else {
            overallCasinoStatus = "BREAK_EVEN";
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalCustomers", totalCustomers);
        response.put("totalSessions", totalSessions);
        response.put("totalCheckIns", totalCheckIns);
        response.put("totalWalletRecords", totalWalletRecords);
        response.put("totalBuyIn", totalBuyIn);
        response.put("totalCashOut", totalCashOut);
        response.put("casinoNet", casinoNet);
        response.put("highValueBuyInAlerts", highValueBuyInAlerts);
        response.put("totalTableWin", totalTableWin);
        response.put("totalTableLoss", totalTableLoss);
        response.put("netPitResult", netPitResult);
        response.put("overallCasinoPosition", overallCasinoPosition);
        response.put("overallCasinoStatus", overallCasinoStatus);



        return response;
    }
}
