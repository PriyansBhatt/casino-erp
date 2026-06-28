package com.casino.casinoerp.controller;

import com.casino.casinoerp.entity.BusinessDate;
import com.casino.casinoerp.service.BusinessDateService;
import org.springframework.web.bind.annotation.*;
import com.casino.casinoerp.dto.ApiResponse;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/business-date")
public class BusinessDateController {

    private final BusinessDateService businessDateService;

    public BusinessDateController(BusinessDateService businessDateService) {
        this.businessDateService = businessDateService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> getCurrentBusinessDate() {
        LocalDate businessDate = businessDateService.getCurrentBusinessDate();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("businessDate", businessDate);
        response.put("rule", "Business day starts at 09:00 AM");

        return ApiResponse.success(
                "Current business date loaded successfully",
                response
        );
    }

    @GetMapping("/all")
    public ApiResponse<List<BusinessDate>> getAllBusinessDates() {
        return ApiResponse.success(
                "Business dates loaded successfully",
                businessDateService.getAll()
        );
    }

    @GetMapping("/current-open")
    public ApiResponse<Object> getCurrentOpenBusinessDate() {
        return ApiResponse.success(
                "Current open business date loaded successfully",
                businessDateService.getCurrentOpenBusinessDate().orElse(null)
        );
    }

    @PostMapping("/reopen/{businessDate}")
    public ApiResponse<BusinessDate> reopenBusinessDate(
            @PathVariable LocalDate businessDate,
            @RequestParam(required = false) String remarks) {

        return ApiResponse.success(
                "Business date reopened successfully",
                businessDateService.reopenBusinessDate(businessDate, remarks)
        );
    }

    @PostMapping("/open/{businessDate}")
    public ApiResponse<BusinessDate> openBusinessDate(
            @PathVariable LocalDate businessDate,
            @RequestParam(required = false) String remarks) {

        return ApiResponse.success(
                "Business date opened successfully",
                businessDateService.openBusinessDate(businessDate, remarks)
        );
    }

    @PostMapping("/close/{businessDate}")
    public ApiResponse<BusinessDate> closeBusinessDate(@PathVariable LocalDate businessDate){
        return ApiResponse.success(
                "Business date closed successfully",
                businessDateService.closeBusinessDate(businessDate)
        );
    }
}