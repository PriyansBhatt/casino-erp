package com.casino.casinoerp.controller;

import com.casino.casinoerp.dto.ApiResponse;
import com.casino.casinoerp.dto.ChipBuyInResponse;
import com.casino.casinoerp.dto.CreateChipBuyInRequest;
import com.casino.casinoerp.dto.ChipBuyInHistoryResponse;
import com.casino.casinoerp.entity.ChipBuyIn;
import com.casino.casinoerp.service.ChipBuyInService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/buyins")
public class ChipBuyInController {

    private final ChipBuyInService service;

    public ChipBuyInController(ChipBuyInService service) {
        this.service = service;
    }

    @GetMapping("/session/{customerSessionId}")
    public List<ChipBuyIn> getBuyInsBySession(@PathVariable UUID customerSessionId) {
        return service.getBySessionId(customerSessionId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ChipBuyInResponse> createBuyIn(
            @Valid @RequestBody CreateChipBuyInRequest request) {
        return ApiResponse.success("Chip buy-in created successfully", service.create(request));
    }

    @GetMapping
    public List<ChipBuyIn> getAllBuyIns() {
        return service.getAllBuyIns();
    }

    @GetMapping("/current")
    public ApiResponse<List<ChipBuyInHistoryResponse>> getCurrentBusinessDateHistory() {
        return ApiResponse.success("Current Business Date Chip Buy-Ins loaded successfully", service.getCurrentBusinessDateHistory());
    }
}
