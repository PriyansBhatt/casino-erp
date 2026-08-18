package com.casino.casinoerp.controller;

import com.casino.casinoerp.dto.ApiResponse;
import com.casino.casinoerp.dto.SessionFinancialPositionResponse;
import com.casino.casinoerp.service.SessionFinancialPositionService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/session-summary")
public class SessionSummaryController {

    private final SessionFinancialPositionService financialPositionService;

    public SessionSummaryController(SessionFinancialPositionService financialPositionService) {
        this.financialPositionService = financialPositionService;
    }

    @GetMapping("/{customerSessionId}")
    public ApiResponse<SessionFinancialPositionResponse> getSessionSummary(
            @PathVariable UUID customerSessionId) {
        return ApiResponse.success(
                "Session financial position loaded successfully",
                financialPositionService.getPosition(customerSessionId)
        );
    }
}
