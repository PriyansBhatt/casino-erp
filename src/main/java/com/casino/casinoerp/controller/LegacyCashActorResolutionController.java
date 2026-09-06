package com.casino.casinoerp.controller;

import com.casino.casinoerp.dto.ApiResponse;
import com.casino.casinoerp.dto.LegacyCashActorResolutionRequest;
import com.casino.casinoerp.dto.LegacyCashActorResolutionResponse;
import com.casino.casinoerp.service.LegacyCashActorResolutionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/cashier-reconciliation")
public class LegacyCashActorResolutionController {
    private final LegacyCashActorResolutionService service;

    public LegacyCashActorResolutionController(LegacyCashActorResolutionService service) {
        this.service = service;
    }

    @PostMapping("/legacy-actor-resolution/{userId}")
    public ApiResponse<LegacyCashActorResolutionResponse> resolve(
            @PathVariable UUID userId,
            @Valid @RequestBody LegacyCashActorResolutionRequest request) {
        return ApiResponse.success("Legacy cash-activity bucket resolved successfully",
                service.resolve(userId, request));
    }

    @GetMapping("/current/legacy-actor-resolutions")
    public ApiResponse<List<LegacyCashActorResolutionResponse>> current() {
        return ApiResponse.success("Legacy cash-activity resolutions loaded successfully",
                service.getCurrent());
    }
}
