package com.casino.casinoerp.controller;

import com.casino.casinoerp.entity.ChipBuyIn;
import com.casino.casinoerp.service.ChipBuyInService;
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
    public ChipBuyIn createBuyIn(@Valid @RequestBody ChipBuyIn buyIn) {
        return service.save(buyIn);
    }

    @GetMapping
    public List<ChipBuyIn> getAllBuyIns() {
        return service.getAllBuyIns();
    }
}