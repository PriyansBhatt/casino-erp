package com.casino.casinoerp.service;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AlertDashboardService {

    private final BusinessDateService businessDateService;
    private final SystemLockService systemLockService;
    private final CustomerSessionService customerSessionService;
    private final PitTableService pitTableService;
    private final WalletTransactionService walletTransactionService;

    public AlertDashboardService(
            BusinessDateService businessDateService,
            SystemLockService systemLockService,
            CustomerSessionService customerSessionService,
            PitTableService pitTableService,
            WalletTransactionService walletTransactionService
    ) {
        this.businessDateService = businessDateService;
        this.systemLockService = systemLockService;
        this.customerSessionService = customerSessionService;
        this.pitTableService = pitTableService;
        this.walletTransactionService = walletTransactionService;
    }

    public Map<String, Object> getDashboardAlerts() {

        Map<String, Object> result = new HashMap<>();

        List<String> alerts = new ArrayList<>();

        boolean businessDateOpen =
                businessDateService.getCurrentBusinessDate() != null;

        boolean systemLocked =
                systemLockService.isSystemLocked();

        long openSessions =
                customerSessionService.getAllSessions()
                        .stream()
                        .filter(s -> "OPEN".equalsIgnoreCase(s.getStatus()))
                        .count();

        long openPitTables =
                pitTableService.getAllTables()
                        .stream()
                        .filter(t -> "OPEN".equalsIgnoreCase(t.getStatus()))
                        .count();

        if (openSessions > 0) {
            alerts.add(openSessions + " customer session(s) still OPEN");
        }

        if (openPitTables > 0) {
            alerts.add(openPitTables + " pit table(s) still OPEN");
        }

        if (systemLocked) {
            alerts.add("System is currently LOCKED");
        }

        long highValueTransactions =
                walletTransactionService.getAll()
                        .stream()
                        .filter(tx -> tx.getAmount() != null)
                        .filter(tx -> tx.getAmount().compareTo(new java.math.BigDecimal("100000")) >= 0)
                        .count();

        if (highValueTransactions > 0) {
            alerts.add(highValueTransactions + " high-value wallet transaction(s)");
        }

        long suspiciousTransactions =
                walletTransactionService.getAll()
                        .stream()
                        .filter(tx -> tx.getAmount() != null)
                        .filter(tx -> tx.getAmount().compareTo(new java.math.BigDecimal("50000")) >= 0)
                        .count();

        if (suspiciousTransactions > 0) {
            alerts.add(suspiciousTransactions + " suspicious wallet transaction(s)");
        }

        long totalAlerts = alerts.size();
        
        result.put("businessDateOpen", businessDateOpen);
        result.put("systemLocked", systemLocked);
        result.put("openSessions", openSessions);
        result.put("openPitTables", openPitTables);
        result.put("alerts", alerts);
        result.put("highValueTransactions", highValueTransactions);
        result.put("suspiciousTransactions", suspiciousTransactions);
        result.put("totalAlerts", totalAlerts);

        return result;
    }
}