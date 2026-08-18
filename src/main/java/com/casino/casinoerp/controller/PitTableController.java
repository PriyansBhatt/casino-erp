package com.casino.casinoerp.controller;

import com.casino.casinoerp.entity.PitTable;
import com.casino.casinoerp.service.PitTableService;
import com.casino.casinoerp.dto.CreatePitTableRequest;
import com.casino.casinoerp.dto.PitTableResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.UUID;


import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/pit-tables")
public class PitTableController {

    private final PitTableService service;

    public PitTableController(PitTableService service) {
        this.service = service;
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

    @GetMapping("/{tableId}/reconciliation")
    public Map<String, Object> getTableReconciliation(@PathVariable UUID tableId) {

        PitTable table = service.getTableById(tableId);

        BigDecimal openingFloat = table.getOpeningFloat();
        BigDecimal closingFloat = table.getClosingFloat();

        BigDecimal tableDifference = openingFloat == null || closingFloat == null
                ? null
                : openingFloat.subtract(closingFloat);

        String tableStatus;

        if (tableDifference == null) {
            tableStatus = "PENDING_CLOSE";
        } else if (tableDifference.compareTo(BigDecimal.ZERO) > 0) {
            tableStatus = "TABLE_PROFIT";
        } else if (tableDifference.compareTo(BigDecimal.ZERO) < 0) {
            tableStatus = "TABLE_LOSS";
        } else {
            tableStatus = "BALANCED";
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("tableId", table.getId());
        response.put("tableCode", table.getTableCode());
        response.put("openingFloat", openingFloat);
        response.put("closingFloat", closingFloat);
        response.put("tableDifference", tableDifference);
        response.put("tableStatus", tableStatus);

        return response;
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
