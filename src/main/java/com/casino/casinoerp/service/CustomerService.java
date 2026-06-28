package com.casino.casinoerp.service;

import com.casino.casinoerp.entity.Customer;
import com.casino.casinoerp.repository.CustomerRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }
    public Customer getCustomerByCode(String customerCode) {
        return customerRepository
                .findByCustomerCode(customerCode)
                .orElse(null);
    }
    public List<Customer> getAllCustomers() {
        return customerRepository.findAll();
    }
}