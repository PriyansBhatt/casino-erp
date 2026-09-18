package com.casino.casinoerp.controller;

import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.service.RunningFundsReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
public class RunningFundsReportController {
    private final RunningFundsReportService service;
    public RunningFundsReportController(RunningFundsReportService service) { this.service = service; }
    @GetMapping({"/daily-operations", "/running-funds"})
    public ApiResponse<RunningFundsReportResponse> daily(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        return ApiResponse.success("Daily Operations loaded", service.getReport(businessDate));
    }
    @GetMapping("/reconciliations")
    public ApiResponse<ReconciliationReportPage> reconciliations(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate businessDate,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="50") int size) {
        return ApiResponse.success("Saved reconciliation snapshots loaded", service.reconciliations(businessDate, page, size));
    }
}
