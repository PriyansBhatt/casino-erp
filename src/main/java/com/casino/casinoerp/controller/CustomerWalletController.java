package com.casino.casinoerp.controller;

import com.casino.casinoerp.entity.CustomerWallet;
import com.casino.casinoerp.service.CustomerWalletService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/wallets")
public class CustomerWalletController {

    private final CustomerWalletService service;

    public CustomerWalletController(CustomerWalletService service) {
        this.service = service;
    }

    @GetMapping
    public List<CustomerWallet> getAllWallets() {
        return service.getAllWallets();
    }

    @GetMapping("/customer/{customerId}")
    public Optional<CustomerWallet> getWalletByCustomerId(@PathVariable UUID customerId) {
        return service.getWalletByCustomerId(customerId);
    }
}