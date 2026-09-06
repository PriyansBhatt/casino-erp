package com.casino.casinoerp.controller;

import com.casino.casinoerp.entity.PitTable;
import com.casino.casinoerp.service.PitTableService;
import com.casino.casinoerp.dto.LegacyPitTableReconciliationRequest;
import com.casino.casinoerp.dto.PitTableResponse;
import com.casino.casinoerp.dto.PitTableReconciliationResponse;
import com.casino.casinoerp.service.LegacyPitTableReconciliationService;
import com.casino.casinoerp.service.PitTableReconciliationService;
import com.casino.casinoerp.service.PitTableOperationService;
import com.casino.casinoerp.service.PitTableModeService;
import com.casino.casinoerp.dto.PitTableModeResponse;
import com.casino.casinoerp.dto.EligiblePitTablePlayerResponse;
import com.casino.casinoerp.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.UUID;


import java.util.List;

@RestController
@RequestMapping("/api/pit-tables")
public class PitTableController {

    private final PitTableService service;
    private final PitTableReconciliationService reconciliationService;
    private final LegacyPitTableReconciliationService legacyReconciliationService;
    private final PitTableOperationService operationService;
    private final PitTableModeService tableModeService;

    public PitTableController(
            PitTableService service,
            PitTableReconciliationService reconciliationService,
            LegacyPitTableReconciliationService legacyReconciliationService,
            PitTableOperationService operationService,
            PitTableModeService tableModeService) {
        this.service = service;
        this.reconciliationService = reconciliationService;
        this.legacyReconciliationService = legacyReconciliationService;
        this.operationService = operationService;
        this.tableModeService = tableModeService;
    }

    @GetMapping("/open")
    public List<PitTableResponse> getOpenTables() {
        return service.getOpenTables().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{tableId}")
    public PitTableResponse getTable(@PathVariable UUID tableId) {
        return toResponse(service.getTableById(tableId));
    }

    @PostMapping("/physical/{physicalTableId}/open")
    @ResponseStatus(HttpStatus.CREATED)
    public PitTableResponse openPhysicalTable(
            @PathVariable UUID physicalTableId,
            @Valid @RequestBody com.casino.casinoerp.dto.OpenPitTableOperationRequest request) {
        return operationService.open(physicalTableId, request);
    }

    @GetMapping("/physical/{physicalTableId}/history")
    public List<PitTableResponse> history(@PathVariable UUID physicalTableId) {
        return operationService.history(physicalTableId);
    }

    @PutMapping("/{tableId}/close")
    public PitTable closeTable(
            @PathVariable UUID tableId,
            @RequestParam BigDecimal closingFloat) {

        return service.closeTable(tableId, closingFloat);
    }

    @PostMapping("/{tableId}/legacy-reconciliation-resolution")
    public PitTableReconciliationResponse resolveLegacyReconciliation(
            @PathVariable UUID tableId,
            @Valid @RequestBody LegacyPitTableReconciliationRequest request) {
        return legacyReconciliationService.resolve(tableId, request);
    }

    @GetMapping("/{tableId}/reconciliation")
    public PitTableReconciliationResponse getTableReconciliation(@PathVariable UUID tableId) {
        return reconciliationService.reconcile(service.getTableById(tableId));
    }
    
    @GetMapping
    public List<com.casino.casinoerp.dto.PitTableOverviewResponse> getAllTables() {
        return operationService.overview();
    }

    @GetMapping("/{tableId}/mode")
    public ApiResponse<PitTableModeResponse> tableMode(@PathVariable UUID tableId) {
        return ApiResponse.success("Pit Table Mode loaded successfully", tableModeService.snapshot(tableId));
    }

    @GetMapping("/{tableId}/eligible-players")
    public ApiResponse<List<EligiblePitTablePlayerResponse>> eligiblePlayers(
            @PathVariable UUID tableId, @RequestParam String query) {
        return ApiResponse.success("Eligible Pit Table players loaded successfully",
                tableModeService.eligiblePlayers(tableId, query));
    }

    private PitTableResponse toResponse(PitTable table) {
        return new PitTableResponse(table.getId(), table.getTableCode(), table.getTableName(),
                table.getGameType(), table.getStatus(), table.getBusinessDate(), table.getOpenedAt(),
                table.getClosedAt(), table.getOpeningFloat(), table.getClosingFloat(), table.getRemarks());
    }
}
