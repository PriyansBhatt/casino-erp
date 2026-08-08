package com.casino.casinoerp.controller;

import com.casino.casinoerp.dto.PrivilegedCustomerResponse;
import com.casino.casinoerp.dto.ReceptionCustomerResponse;
import com.casino.casinoerp.service.CustomerService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    public List<ReceptionCustomerResponse> getAllCustomers() {
        return customerService.getAllCustomers();
    }

    @GetMapping("/search")
    public List<ReceptionCustomerResponse> searchCustomers(@RequestParam String query) {
        return customerService.searchCustomers(query);
    }

    @GetMapping("/id/{customerId}")
    public PrivilegedCustomerResponse getCustomerById(@PathVariable UUID customerId) {
        return customerService.getPrivilegedCustomerById(customerId);
    }

    @GetMapping("/{customerCode}")
    public ReceptionCustomerResponse getCustomer(@PathVariable String customerCode) {
        return customerService.getCustomerByCode(customerCode);
    }
}
