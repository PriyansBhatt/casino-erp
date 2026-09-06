package com.casino.casinoerp.controller;

import com.casino.casinoerp.dto.ApiResponse;
import com.casino.casinoerp.dto.OperationalStatusResponse;
import com.casino.casinoerp.service.OperationalStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/business-status")
public class BusinessStatusController {
    private final OperationalStatusService service;

    public BusinessStatusController(OperationalStatusService service) {
        this.service = service;
    }

    @GetMapping("/current")
    public ApiResponse<OperationalStatusResponse> current() {
        return ApiResponse.success("Operational status loaded successfully", service.current());
    }
}
