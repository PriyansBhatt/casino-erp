package com.casino.casinoerp.controller;

import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.service.LosingReturnService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/losing-returns")
public class LosingReturnController {
    private final LosingReturnService service;
    public LosingReturnController(LosingReturnService service){ this.service=service; }
    @GetMapping("/eligibility/customer/{customerId}")
    public ApiResponse<LosingReturnEligibilityResponse> eligibility(@PathVariable UUID customerId){
        return ApiResponse.success("Losing Return eligibility calculated successfully", service.eligibility(customerId));
    }
    @GetMapping("/history/customer/{customerId}")
    public ApiResponse<java.util.List<LosingReturnHistoryResponse>> history(@PathVariable UUID customerId,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate businessDate) {
        return ApiResponse.success("Persisted Losing Return history", service.history(customerId, businessDate));
    }
    @PostMapping
    public ApiResponse<LosingReturnResponse> create(@Valid @RequestBody CreateLosingReturnRequest request){
        return ApiResponse.success("Losing Return posted successfully", service.create(request));
    }
}
