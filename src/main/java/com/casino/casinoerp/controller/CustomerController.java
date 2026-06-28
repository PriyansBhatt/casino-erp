package com.casino.casinoerp.controller;

import com.casino.casinoerp.entity.Customer;
import com.casino.casinoerp.service.CustomerService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    public List<Customer> getAllCustomers() {
        return customerService.getAllCustomers();
    }
    @GetMapping("/{customerCode}")
    public Customer getCustomer(@PathVariable String customerCode) {
        return customerService.getCustomerByCode(customerCode);
    }
}