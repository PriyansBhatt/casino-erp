package com.casino.casinoerp.controller;

import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.service.VerifiedGamingResultService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/verified-gaming-results")
public class VerifiedGamingResultController {

    private final VerifiedGamingResultService service;

    public VerifiedGamingResultController(VerifiedGamingResultService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<VerifiedGamingResultResponse> create(
            @Valid @RequestBody CreateVerifiedGamingResultRequest request) {
        return ApiResponse.success("Verified gaming result recorded successfully", service.create(request));
    }

    @GetMapping("/session/{sessionId}")
    public ApiResponse<List<VerifiedGamingResultResponse>> getBySession(@PathVariable UUID sessionId) {
        return ApiResponse.success("Verified gaming results loaded successfully", service.getBySession(sessionId));
    }
}
