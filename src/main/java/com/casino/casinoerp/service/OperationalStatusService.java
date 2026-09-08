package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.OperationalStatusResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationalStatusService {
    private final BusinessDateService businessDates;
    private final SystemLockService systemLock;

    public OperationalStatusService(BusinessDateService businessDates, SystemLockService systemLock) {
        this.businessDates = businessDates;
        this.systemLock = systemLock;
    }

    @Transactional(readOnly = true)
    public OperationalStatusResponse current() {
        var health = businessDates.getHealth();
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
