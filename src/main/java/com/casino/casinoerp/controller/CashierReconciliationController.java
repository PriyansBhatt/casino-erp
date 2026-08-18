package com.casino.casinoerp.controller;

import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.service.CashierReconciliationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/cashier-reconciliation")
public class CashierReconciliationController {
    private final CashierReconciliationService service;
    public CashierReconciliationController(CashierReconciliationService service) { this.service = service; }

    @GetMapping("/current")
    public ApiResponse<CashierReconciliationResponse> current() {
        return ApiResponse.success("Current cashier reconciliation loaded successfully", service.getCurrent());
    }
    @PostMapping("/preview")
    public ApiResponse<CashierReconciliationResponse> preview(@Valid @RequestBody CashierReconciliationRequest request) {
        return ApiResponse.success("Cashier reconciliation calculated successfully", service.preview(request));
    }
    @PostMapping("/submit")
    public ApiResponse<CashierReconciliationResponse> submit(@Valid @RequestBody CashierReconciliationRequest request) {
        return ApiResponse.success("Cashier reconciliation submitted successfully", service.submit(request));
    }
    @GetMapping("/current/submitted")
    public ApiResponse<List<CashierReconciliationResponse>> submittedForCurrentBusinessDate() {
        return ApiResponse.success("Submitted cashier reconciliations loaded successfully", service.getSubmittedForCurrentBusinessDate());
    }
    @PostMapping("/{id}/reopen")
    public ApiResponse<CashierReconciliationResponse> reopen(@PathVariable UUID id,
            @Valid @RequestBody ReopenCashierReconciliationRequest request) {
        return ApiResponse.success("Cashier reconciliation reopened successfully", service.reopen(id, request.reason()));
    }
}
