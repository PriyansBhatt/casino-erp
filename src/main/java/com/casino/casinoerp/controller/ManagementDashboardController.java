package com.casino.casinoerp.controller;

import com.casino.casinoerp.dto.ApiResponse;
import com.casino.casinoerp.dto.ManagementDashboardResponse;
import com.casino.casinoerp.service.ManagementDashboardService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/dashboard/management")
public class ManagementDashboardController {
    private final ManagementDashboardService service;
    public ManagementDashboardController(ManagementDashboardService service) { this.service = service; }

    @GetMapping
    public ApiResponse<ManagementDashboardResponse> getDashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        return ApiResponse.success("Management dashboard loaded successfully", service.getDashboard(businessDate));
    }
}
