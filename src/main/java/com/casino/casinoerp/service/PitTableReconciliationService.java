package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.PitTableReconciliationResponse;
import com.casino.casinoerp.entity.LegacyPitTableReconciliationResolution;
import com.casino.casinoerp.entity.PitTable;
import com.casino.casinoerp.repository.LegacyPitTableReconciliationResolutionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class PitTableReconciliationService {
    private final LegacyPitTableReconciliationResolutionRepository legacyResolutions;

    public PitTableReconciliationService(LegacyPitTableReconciliationResolutionRepository legacyResolutions) {
        this.legacyResolutions = legacyResolutions;
    }

    public PitTableReconciliationResponse reconcile(PitTable table) {
        return legacyResolutions.findByPitTableId(table.getId())
                .map(resolution -> legacyResponse(table, resolution))
                .orElseGet(() -> normalResponse(table));
    }

    private PitTableReconciliationResponse legacyResponse(
            PitTable table, LegacyPitTableReconciliationResolution resolution) {
        return new PitTableReconciliationResponse(
                table.getId(), table.getTableCode(), table.getOpeningFloat(),
                resolution.getPhysicalClosingFloat(), null, "LEGACY_RESOLVED",
                resolution.getOpeningFloatVerification(), resolution.getLegacyOpeningFloatGap(),
                resolution.getReason(), resolution.getResolvedBy(), resolution.getResolvedAt(),
                resolution.getIdempotencyKey());
    }

    private PitTableReconciliationResponse normalResponse(PitTable table) {
        BigDecimal difference = table.getOpeningFloat() == null || table.getClosingFloat() == null
                ? null : table.getOpeningFloat().subtract(table.getClosingFloat());
        String status = difference == null ? "PENDING_CLOSE"
                : difference.signum() > 0 ? "TABLE_PROFIT"
                : difference.signum() < 0 ? "TABLE_LOSS" : "BALANCED";
        return new PitTableReconciliationResponse(
                table.getId(), table.getTableCode(), table.getOpeningFloat(), table.getClosingFloat(),
                difference, status, null, null, null, null, null, null);
    }
}
