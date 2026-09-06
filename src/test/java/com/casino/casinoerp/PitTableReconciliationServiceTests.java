package com.casino.casinoerp;

import com.casino.casinoerp.entity.LegacyPitTableReconciliationResolution;
import com.casino.casinoerp.entity.PitTable;
import com.casino.casinoerp.repository.LegacyPitTableReconciliationResolutionRepository;
import com.casino.casinoerp.service.PitTableReconciliationService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PitTableReconciliationServiceTests {
    private final LegacyPitTableReconciliationResolutionRepository resolutions =
            mock(LegacyPitTableReconciliationResolutionRepository.class);
    private final PitTableReconciliationService service = new PitTableReconciliationService(resolutions);

    @Test
    void legacyOpeningGapIsNotClassifiedAsTableProfit() {
        PitTable table = table("100000", "0");
        LegacyPitTableReconciliationResolution resolution = new LegacyPitTableReconciliationResolution();
        resolution.setPhysicalClosingFloat(BigDecimal.ZERO);
        resolution.setLegacyOpeningFloatGap(new BigDecimal("100000"));
        resolution.setOpeningFloatVerification("LEGACY_UNVERIFIED");
        when(resolutions.findByPitTableId(table.getId())).thenReturn(Optional.of(resolution));

        var result = service.reconcile(table);
        assertThat(result.tableStatus()).isEqualTo("LEGACY_RESOLVED");
        assertThat(result.tableDifference()).isNull();
        assertThat(result.legacyOpeningFloatGap()).isEqualByComparingTo("100000");
    }

    @Test
    void ordinaryReconciliationRemainsUnchanged() {
        PitTable table = table("100000", "90000");
        when(resolutions.findByPitTableId(table.getId())).thenReturn(Optional.empty());
        var result = service.reconcile(table);
        assertThat(result.tableStatus()).isEqualTo("TABLE_PROFIT");
        assertThat(result.tableDifference()).isEqualByComparingTo("10000");
        assertThat(result.openingFloatVerification()).isNull();
    }

    private PitTable table(String opening, String closing) {
        PitTable table = new PitTable();
        table.setId(UUID.randomUUID());
        table.setTableCode("T-BAC-22");
        table.setOpeningFloat(new BigDecimal(opening));
        table.setClosingFloat(new BigDecimal(closing));
        return table;
    }
}
