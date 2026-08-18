package com.casino.casinoerp.controller;

import com.casino.casinoerp.dto.ApiResponse;
import com.casino.casinoerp.dto.ChipCashOutResponse;
import com.casino.casinoerp.dto.CreateChipCashOutRequest;
import com.casino.casinoerp.service.ChipCashOutService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/cashouts")
public class ChipCashOutController {

    private final ChipCashOutService service;

    public ChipCashOutController(ChipCashOutService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ChipCashOutResponse> createCashOut(
            @Valid @RequestBody CreateChipCashOutRequest request) {
        return ApiResponse.success("Chip cash-out created successfully", service.create(request));
    }
    

    @GetMapping("/session/{customerSessionId}")
    public List<ChipCashOutResponse> getCashOutsBySession(@PathVariable UUID customerSessionId) {
        return service.getBySessionId(customerSessionId);
    }

    @GetMapping
    public List<ChipCashOutResponse> getAllCashOuts() {
        return service.getAllCashOuts();
    }
}
