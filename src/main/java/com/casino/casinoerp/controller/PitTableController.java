package com.casino.casinoerp.controller;

import com.casino.casinoerp.entity.PitTable;
import com.casino.casinoerp.service.PitTableService;
import com.casino.casinoerp.dto.CreatePitTableRequest;
import com.casino.casinoerp.dto.LegacyPitTableReconciliationRequest;
import com.casino.casinoerp.dto.PitTableResponse;
import com.casino.casinoerp.dto.PitTableReconciliationResponse;
import com.casino.casinoerp.service.LegacyPitTableReconciliationService;
import com.casino.casinoerp.service.PitTableReconciliationService;
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

    public PitTableController(
            PitTableService service,
            PitTableReconciliationService reconciliationService,
            LegacyPitTableReconciliationService legacyReconciliationService) {
        this.service = service;
        this.reconciliationService = reconciliationService;
        this.legacyReconciliationService = legacyReconciliationService;
    }

    @GetMapping("/open")
    public List<PitTableResponse> getOpenTables() {
        return service.getOpenTables().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{tableId}")
    public PitTableResponse getTable(@PathVariable UUID tableId) {
        return toResponse(service.getTableById(tableId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PitTableResponse createAndOpen(@Valid @RequestBody CreatePitTableRequest request) {
        return toResponse(service.createAndOpen(request));
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
    public List<PitTableResponse> getAllTables() {
        return service.getAllTables().stream().map(this::toResponse).toList();
    }

    private PitTableResponse toResponse(PitTable table) {
        return new PitTableResponse(table.getId(), table.getTableCode(), table.getTableName(),
                table.getGameType(), table.getStatus(), table.getBusinessDate(), table.getOpenedAt(),
                table.getClosedAt(), table.getOpeningFloat(), table.getClosingFloat(), table.getRemarks());
    }
}
