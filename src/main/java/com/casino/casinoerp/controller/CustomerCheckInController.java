package com.casino.casinoerp.controller;

import com.casino.casinoerp.entity.CustomerCheckIn;
import com.casino.casinoerp.service.CustomerCheckInService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/checkins")
public class CustomerCheckInController {

    private final CustomerCheckInService customerCheckInService;

    public CustomerCheckInController(CustomerCheckInService customerCheckInService) {
        this.customerCheckInService = customerCheckInService;
    }

    @GetMapping
    public List<CustomerCheckIn> getAllCheckIns() {
        return customerCheckInService.getAllCheckIns();
    }
}