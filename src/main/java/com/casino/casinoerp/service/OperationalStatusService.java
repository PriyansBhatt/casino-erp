package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.OperationalStatusResponse;
import com.casino.casinoerp.entity.BusinessDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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
        var open = businessDates.getCurrentOpenBusinessDate();
        return new OperationalStatusResponse(
                open.map(BusinessDate::getBusinessDate).orElse(null),
                open.isPresent(),
                systemLock.isSystemLocked(),
                lockReason(),
                LocalDateTime.now());
    }

    private String lockReason() {
        if (systemLock.isManuallyLocked()) return "Manual system lock";
        if (systemLock.isScheduledLockActive() && !systemLock.isEmergencyUnlockActive()) {
            return "Scheduled settlement period";
        }
        return null;
    }
}
