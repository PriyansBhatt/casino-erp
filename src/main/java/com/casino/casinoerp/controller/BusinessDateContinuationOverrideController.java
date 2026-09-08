package com.casino.casinoerp.controller;

import com.casino.casinoerp.dto.ApiResponse;
import com.casino.casinoerp.dto.BusinessDateContinuationOverrideResponse;
import com.casino.casinoerp.dto.CreateBusinessDateContinuationOverrideRequest;
import com.casino.casinoerp.dto.RevokeBusinessDateContinuationOverrideRequest;
import com.casino.casinoerp.service.BusinessDateContinuationOverrideService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/business-date/continuation-override")
public class BusinessDateContinuationOverrideController {
    private final BusinessDateContinuationOverrideService service;

    public BusinessDateContinuationOverrideController(BusinessDateContinuationOverrideService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<BusinessDateContinuationOverrideResponse> current() {
        return ApiResponse.success("Business Date continuation override loaded successfully",
                service.current().orElse(null));
    }

    @PostMapping
    public ApiResponse<BusinessDateContinuationOverrideResponse> create(
            @Valid @RequestBody CreateBusinessDateContinuationOverrideRequest request) {
        return ApiResponse.success("Business Date continuation override created successfully",
                service.create(request));
    }

    @DeleteMapping
    public ApiResponse<BusinessDateContinuationOverrideResponse> revoke(
            @Valid @RequestBody RevokeBusinessDateContinuationOverrideRequest request) {
        return ApiResponse.success("Business Date continuation override revoked successfully",
                service.revoke(request));
    }
}
