package com.casino.casinoerp.controller;

import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.service.ChipCustodyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/chip-custody")
public class ChipCustodyController {
    private final ChipCustodyService service;

    public ChipCustodyController(ChipCustodyService service) {
        this.service = service;
    }

    @PostMapping("/cage/opening")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ChipCustodyMovementResponse> initializeCage(
            @Valid @RequestBody ChipCustodyTransferRequest request) {
        return ApiResponse.success("Cage opening chip inventory recorded successfully", service.initializeCage(request));
    }

    @PostMapping("/legacy-session-correction")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ChipCustodyMovementResponse> correctLegacySessionCustody(
            @Valid @RequestBody LegacySessionCustodyCorrectionRequest request) {
        return ApiResponse.success("Legacy customer-session custody corrected successfully",
                service.correctLegacySessionCustody(request));
    }

    @GetMapping("/cage")
    public ApiResponse<ChipCustodyInventoryResponse> cageInventory() {
        return ApiResponse.success("Cage chip inventory loaded successfully", service.cageInventory());
    }

    @GetMapping("/customer-sessions/{sessionId}")
    public ApiResponse<ChipCustodyInventoryResponse> customerSessionInventory(@PathVariable UUID sessionId) {
        return ApiResponse.success("Customer-session chip custody loaded successfully",
                service.customerSessionInventory(sessionId));
    }

    @GetMapping("/tables/{tableId}")
    public ApiResponse<ChipCustodyInventoryResponse> tableInventory(@PathVariable UUID tableId) {
        return ApiResponse.success("Pit Table chip inventory loaded successfully", service.tableInventory(tableId));
    }

    @PostMapping("/tables/{tableId}/float-issue")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ChipCustodyMovementResponse> issueTableFloat(
            @PathVariable UUID tableId, @Valid @RequestBody ChipCustodyTransferRequest request) {
        return ApiResponse.success("Pit Table float issued successfully", service.issueTableFloat(tableId, request));
    }

    @PostMapping("/tables/{tableId}/float-return")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ChipCustodyMovementResponse> returnTableFloat(
            @PathVariable UUID tableId, @Valid @RequestBody ChipCustodyTransferRequest request) {
        return ApiResponse.success("Pit Table float returned successfully", service.returnTableFloat(tableId, request));
    }

    @PostMapping("/tables/{tableId}/customer-sessions/{sessionId}/issue")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ChipCustodyMovementResponse> moveCustomerChipsToTable(
            @PathVariable UUID tableId, @PathVariable UUID sessionId,
            @Valid @RequestBody ChipCustodyTransferRequest request) {
        return ApiResponse.success("Customer chips moved to Pit Table successfully",
                service.moveCustomerChipsToTable(tableId, sessionId, request));
    }

    @PostMapping("/tables/{tableId}/customer-sessions/{sessionId}/return")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ChipCustodyMovementResponse> returnTableChipsToCustomer(
            @PathVariable UUID tableId, @PathVariable UUID sessionId,
            @Valid @RequestBody ChipCustodyTransferRequest request) {
        return ApiResponse.success("Pit Table chips returned to customer successfully",
                service.returnTableChipsToCustomer(tableId, sessionId, request));
    }

    @GetMapping("/movements/current")
    public ApiResponse<List<ChipCustodyMovementResponse>> currentHistory() {
        return ApiResponse.success("Current Business Date chip custody movements loaded successfully",
                service.currentHistory());
    }
}
