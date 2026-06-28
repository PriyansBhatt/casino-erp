package com.casino.casinoerp.controller;

import com.casino.casinoerp.entity.ChipCashOut;
import com.casino.casinoerp.service.ChipCashOutService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/cashouts")
public class ChipCashOutController {

    private final ChipCashOutService service;

    public ChipCashOutController(ChipCashOutService service) {
        this.service = service;
    }
    

    @GetMapping("/session/{customerSessionId}")
    public List<ChipCashOut> getCashOutsBySession(@PathVariable UUID customerSessionId) {
        return service.getBySessionId(customerSessionId);
    }

    @GetMapping
    public List<ChipCashOut> getAllCashOuts() {
        return service.getAllCashOuts();
    }
}