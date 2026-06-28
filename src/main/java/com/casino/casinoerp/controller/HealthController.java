package com.casino.casinoerp.controller;

import com.casino.casinoerp.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public ApiResponse<Map<String, Object>> health() {

        Map<String, Object> data = Map.of(
                "status", "UP",
                "service", "Casino ERP Backend"
        );

        return ApiResponse.success(
                "Backend is running",
                data
        );
    }
}