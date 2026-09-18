package com.casino.casinoerp.dto;

import java.time.LocalDate;
import java.util.List;

public record ReconciliationReportPage(LocalDate businessDate, String status,
        List<RunningFundsReconciliationResponse> items, int page, int size, boolean hasNext) {}
