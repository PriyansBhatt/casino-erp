package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.OperationalStatusResponse;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationalStatusService {
    private final BusinessDateService businessDates;
    private final SystemLockService systemLock;
    private final BusinessDateContinuationOverrideService continuationOverrides;

    @Autowired
    public OperationalStatusService(BusinessDateService businessDates, SystemLockService systemLock,
            BusinessDateContinuationOverrideService continuationOverrides) {
        this.businessDates = businessDates;
        this.systemLock = systemLock;
        this.continuationOverrides = continuationOverrides;
    }

    public OperationalStatusService(BusinessDateService businessDates, SystemLockService systemLock) {
        this(businessDates, systemLock, null);
    }

    @Transactional(readOnly = true)
    public OperationalStatusResponse current() {
        var health = businessDates.getHealth();
        var continuation = continuationOverrides == null ? null : continuationOverrides.current().orElse(null);
        return new OperationalStatusResponse(
                health.businessDate(),
                health.expectedBusinessDate(),
                health.businessDate() != null,
                health.health(),
                health.stale(),
                health.staleByDays(),
                health.lifecycleWarning(),
                systemLock.isSystemLocked(),
                lockReason(),
                continuation != null && continuation.active(),
                continuation == null ? null : continuation.expiresAt(),
                continuation == null ? null : continuation.reason(),
                continuation == null ? null : continuation.actor(),
                businessDates.currentCasinoDateTime());
    }

    private String lockReason() {
        if (systemLock.isManuallyLocked()) return "Manual system lock";
        if (systemLock.isScheduledLockActive() && !systemLock.isEmergencyUnlockActive()) {
            return "Scheduled settlement period";
        }
        return null;
    }
}
