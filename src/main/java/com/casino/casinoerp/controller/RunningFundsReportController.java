package com.casino.casinoerp.controller;

import com.casino.casinoerp.dto.ApiResponse;
import com.casino.casinoerp.dto.RunningFundsReportResponse;
import com.casino.casinoerp.service.RunningFundsReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports/running-funds")
public class RunningFundsReportController {
    private final RunningFundsReportService service;

    public RunningFundsReportController(RunningFundsReportService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<RunningFundsReportResponse> getReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        return ApiResponse.success("Running Funds Report loaded successfully", service.getReport(businessDate));
    }
}
