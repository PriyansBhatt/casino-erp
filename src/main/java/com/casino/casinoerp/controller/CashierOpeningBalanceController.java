package com.casino.casinoerp.controller;

import com.casino.casinoerp.dto.ApiResponse;
import com.casino.casinoerp.dto.CashierOpeningBalanceResponse;
import com.casino.casinoerp.dto.CreateCashierOpeningBalanceRequest;
import com.casino.casinoerp.service.CashierOpeningBalanceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cashier-opening-balances")
public class CashierOpeningBalanceController {
    private final CashierOpeningBalanceService service;

    public CashierOpeningBalanceController(CashierOpeningBalanceService service) {
        this.service = service;
    }

    @GetMapping("/current")
    public ApiResponse<CashierOpeningBalanceResponse> current() {
        return ApiResponse.success("Current cashier Opening Cash loaded successfully", service.getCurrent());
    }

    @PostMapping("/current")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CashierOpeningBalanceResponse> create(
            @Valid @RequestBody CreateCashierOpeningBalanceRequest request) {
        return ApiResponse.success("Cashier Opening Cash established successfully", service.create(request));
    }
}
