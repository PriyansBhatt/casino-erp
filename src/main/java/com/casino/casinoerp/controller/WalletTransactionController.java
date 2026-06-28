package com.casino.casinoerp.controller;

import com.casino.casinoerp.entity.WalletTransaction;
import com.casino.casinoerp.service.WalletTransactionService;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/wallet-transactions")
public class WalletTransactionController {

    private final WalletTransactionService service;

    public WalletTransactionController(WalletTransactionService service) {
        this.service = service;
    }

    @GetMapping
    public List<WalletTransaction> getAll() {
        return service.getAll();
    }

    @GetMapping("/customer/{customerId}")
    public List<WalletTransaction> getByCustomer(@PathVariable UUID customerId) {
        return service.getByCustomer(customerId);
    }

    @GetMapping("/session/{sessionId}")
    public List<WalletTransaction> getBySession(@PathVariable UUID sessionId) {
        return service.getBySession(sessionId);
    }

    @GetMapping("/balance/customer/{customerId}")
    public Map<String, Object> getCustomerBalance(@PathVariable UUID customerId) {

        BigDecimal balance = service.getCustomerBalance(customerId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("customerId", customerId);
        response.put("walletBalance", balance);

        return response;
    }

    @GetMapping("/business-date/{businessDate}")
    public List<WalletTransaction> getByBusinessDate(
            @PathVariable LocalDate businessDate) {

        return service.getByBusinessDate(businessDate);
    }

    @GetMapping("/balance/customer/{customerId}/session/{sessionId}")
    public Map<String, Object> getSessionBalance(
            @PathVariable UUID customerId,
            @PathVariable UUID sessionId) {

        BigDecimal balance = service.getSessionBalance(customerId, sessionId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("customerId", customerId);
        response.put("customerSessionId", sessionId);
        response.put("walletBalance", balance);

        return response;
    }

    @PostMapping
    public WalletTransaction create(@Valid @RequestBody WalletTransaction transaction) {
        return service.save(transaction);
    }
}