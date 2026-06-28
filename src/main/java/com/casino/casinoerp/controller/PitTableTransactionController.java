package com.casino.casinoerp.controller;

import com.casino.casinoerp.entity.PitTableTransaction;
import com.casino.casinoerp.service.PitTableTransactionService;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/pit-table-transactions")
public class PitTableTransactionController {

    private final PitTableTransactionService service;

    public PitTableTransactionController(PitTableTransactionService service) {
        this.service = service;
    }

    @GetMapping
    public List<PitTableTransaction> getAllTransactions() {
        return service.getAllTransactions();
    }

    @PostMapping
    public PitTableTransaction createTransaction(@Valid @RequestBody PitTableTransaction transaction){
        return service.save(transaction);
    }

    @GetMapping("/table/{pitTableId}")
    public List<PitTableTransaction> getTransactionsByTable(@PathVariable UUID pitTableId) {

        return service.getTransactionsByTable(pitTableId);
    }
}