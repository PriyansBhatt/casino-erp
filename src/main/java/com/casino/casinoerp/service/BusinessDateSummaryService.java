package com.casino.casinoerp.service;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Service
public class BusinessDateSummaryService {

    private final CustomerSessionService customerSessionService;
    private final PitTableService pitTableService;
    private final WalletTransactionService walletTransactionService;
    private final PitTableTransactionService pitTableTransactionService;
    private final AuditLogService auditLogService;

    public BusinessDateSummaryService(
            CustomerSessionService customerSessionService,
            PitTableService pitTableService,
            WalletTransactionService walletTransactionService,
            PitTableTransactionService pitTableTransactionService,
            AuditLogService auditLogService
    ) {
        this.customerSessionService = customerSessionService;
        this.pitTableService = pitTableService;
        this.walletTransactionService = walletTransactionService;
        this.pitTableTransactionService = pitTableTransactionService;
        this.auditLogService = auditLogService;
    }

    public Map<String, Object> getSummary(LocalDate businessDate) {

        Map<String, Object> summary = new HashMap<>();

        long totalSessions = customerSessionService.getAllSessions()
                .stream()
                .filter(s -> businessDate.equals(s.getBusinessDate()))
                .count();

        long openSessions = customerSessionService.getAllSessions()
                .stream()
                .filter(s -> businessDate.equals(s.getBusinessDate()))
                .filter(s -> "OPEN".equalsIgnoreCase(s.getStatus()))
                .count();

        long closedSessions = customerSessionService.getAllSessions()
                .stream()
                .filter(s -> businessDate.equals(s.getBusinessDate()))
                .filter(s -> "CLOSED".equalsIgnoreCase(s.getStatus()))
                .count();

        long openPitTables = pitTableService.getAllTables()
                .stream()
                .filter(t -> businessDate.equals(t.getBusinessDate()))
                .filter(t -> "OPEN".equalsIgnoreCase(t.getStatus()))
                .count();

        long closedPitTables = pitTableService.getAllTables()
                .stream()
                .filter(t -> businessDate.equals(t.getBusinessDate()))
                .filter(t -> "CLOSED".equalsIgnoreCase(t.getStatus()))
                .count();

        long totalWalletTransactions = walletTransactionService.getAll()
                .stream()
                .filter(w -> businessDate.equals(w.getBusinessDate()))
                .count();

        long totalPitTransactions = pitTableTransactionService.getAllTransactions()
                .stream()
                .filter(p -> businessDate.equals(p.getBusinessDate()))
                .count();

        long totalAuditLogs = auditLogService.getByBusinessDate(businessDate).size();

        summary.put("businessDate", businessDate);
        summary.put("totalSessions", totalSessions);
        summary.put("openSessions", openSessions);
        summary.put("closedSessions", closedSessions);
        summary.put("openPitTables", openPitTables);
        summary.put("closedPitTables", closedPitTables);
        summary.put("totalWalletTransactions", totalWalletTransactions);
        summary.put("totalPitTransactions", totalPitTransactions);
        summary.put("totalAuditLogs", totalAuditLogs);

        return summary;
    }
}