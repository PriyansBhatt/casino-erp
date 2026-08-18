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
    @PostMapping
    public ApiResponse<LosingReturnResponse> create(@Valid @RequestBody CreateLosingReturnRequest request){
        return ApiResponse.success("Losing Return posted successfully", service.create(request));
    }
}
