package com.casino.casinoerp.dto;

import java.math.BigDecimal;
import java.time.*;
import java.util.Map;

public record ManagementDashboardResponse(
        LocalDate businessDate, String businessDateStatus, OffsetDateTime lastUpdated,
        String timeZone, OffsetDateTime windowStart, OffsetDateTime windowEndExclusive,
        Map<String, Metric> summary) {

    public record Metric(BigDecimal value, boolean available, String description) {
        public static Metric available(BigDecimal value, String description) {
            return new Metric(value, true, description);
        }
        public static Metric unavailable(String reason) {
            return new Metric(null, false, reason);
        }
    }
}
