package com.casino.casinoerp.controller;

import com.casino.casinoerp.entity.CustomerServiceRecord;
import com.casino.casinoerp.service.CustomerServiceRecordService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/customer-services")
public class CustomerServiceRecordController {

    private final CustomerServiceRecordService service;

    public CustomerServiceRecordController(CustomerServiceRecordService service) {
        this.service = service;
    }

    @GetMapping
    public List<CustomerServiceRecord> getAll() {
        return service.getAll();
    }

    @GetMapping("/customer/{customerId}")
    public List<CustomerServiceRecord> getByCustomer(@PathVariable UUID customerId) {
        return service.getByCustomer(customerId);
    }

    @GetMapping("/session/{sessionId}")
    public List<CustomerServiceRecord> getBySession(@PathVariable UUID sessionId) {
        return service.getBySession(sessionId);
    }
    
    @PostMapping
    public CustomerServiceRecord create(@RequestBody CustomerServiceRecord record) {
        return service.save(record);
    }
}