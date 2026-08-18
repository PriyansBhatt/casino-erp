package com.casino.casinoerp.controller;

import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.service.CustomerBonusService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/customer-bonuses")
public class CustomerBonusController {
    private final CustomerBonusService service;
    public CustomerBonusController(CustomerBonusService service) { this.service = service; }

    @GetMapping
    public ApiResponse<List<CustomerBonusResponse>> getBonuses(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        return ApiResponse.success("Customer bonuses loaded successfully", service.getByBusinessDate(businessDate));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CustomerBonusResponse> create(@Valid @RequestBody CreateCustomerBonusRequest request) {
        return ApiResponse.success("Customer bonus created and approved successfully", service.create(request));
    }
}
