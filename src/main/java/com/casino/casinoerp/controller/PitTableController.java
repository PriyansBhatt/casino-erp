package com.casino.casinoerp.controller;

import com.casino.casinoerp.entity.PitTable;
import com.casino.casinoerp.service.PitTableService;
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
    public List<PitTable> getOpenTables() {
        return service.getOpenTables();
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

        BigDecimal tableDifference = openingFloat.subtract(closingFloat);

        String tableStatus;

        if (tableDifference.compareTo(BigDecimal.ZERO) > 0) {
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
    public List<PitTable> getAllTables() {
        return service.getAllTables();
    }
}