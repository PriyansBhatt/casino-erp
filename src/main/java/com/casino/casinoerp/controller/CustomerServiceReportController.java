package com.casino.casinoerp.controller;

import com.casino.casinoerp.entity.CustomerServiceRecord;
import com.casino.casinoerp.service.CustomerServiceRecordService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/customer-service-report")
public class CustomerServiceReportController {

    private final CustomerServiceRecordService serviceRecordService;

    public CustomerServiceReportController(CustomerServiceRecordService serviceRecordService) {
        this.serviceRecordService = serviceRecordService;
    }


    @GetMapping("/business-date/{businessDate}")
    public Map<String, Object> getServiceReportByBusinessDate(
            @PathVariable java.time.LocalDate businessDate) {

        List<CustomerServiceRecord> services =
                serviceRecordService.getByBusinessDate(businessDate);

        Map<String, Long> serviceTypeSummary = services.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        CustomerServiceRecord::getServiceType,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.counting()
                ));

        BigDecimal totalServiceCost = services.stream()
                .map(service -> service.getServiceCost() == null ? BigDecimal.ZERO : service.getServiceCost())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("businessDate", businessDate);
        response.put("serviceCount", services.size());
        response.put("services", services);
        response.put("serviceTypeSummary", serviceTypeSummary);
        response.put("totalServiceCost", totalServiceCost);

        return response;
    }

    @GetMapping("/customer/{customerId}/business-date/{businessDate}")
    public Map<String, Object> getCustomerServiceReportByBusinessDate(
            @PathVariable UUID customerId,
            @PathVariable java.time.LocalDate businessDate) {

        List<CustomerServiceRecord> services =
                serviceRecordService.getByCustomer(customerId)
                        .stream()
                        .filter(service -> businessDate.equals(service.getBusinessDate()))
                        .toList();

        Map<String, Long> serviceTypeSummary = services.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        CustomerServiceRecord::getServiceType,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.counting()
                ));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("customerId", customerId);
        response.put("businessDate", businessDate);
        response.put("serviceCount", services.size());
        response.put("serviceTypeSummary", serviceTypeSummary);
        response.put("services", services);

        return response;
    }


    @GetMapping("/customer/{customerId}")
    public Map<String, Object> getCustomerServiceReport(@PathVariable UUID customerId) {

        List<CustomerServiceRecord> services =
                serviceRecordService.getByCustomer(customerId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("customerId", customerId);
        response.put("serviceCount", services.size());
        response.put("services", services);

        return response;
    }
}