package com.casino.casinoerp.controller;

import com.casino.casinoerp.dto.ApiResponse;
import com.casino.casinoerp.service.AlertDashboardService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertDashboardService alertDashboardService;

    public AlertController(AlertDashboardService alertDashboardService) {
        this.alertDashboardService = alertDashboardService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> getDashboardAlerts() {
        return ApiResponse.success(
                "Alert dashboard loaded successfully",
                alertDashboardService.getDashboardAlerts()
        );
    }
}