package com.casino.casinoerp.controller;

import com.casino.casinoerp.dto.ApiResponse;
import com.casino.casinoerp.dto.EmergencyUnlockRequest;
import com.casino.casinoerp.service.BusinessDateService;
import com.casino.casinoerp.service.SystemLockService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/system-lock")
public class SystemLockController {

    private final SystemLockService systemLockService;
    private final BusinessDateService businessDateService;

    public SystemLockController(SystemLockService systemLockService,
            BusinessDateService businessDateService) {
        this.systemLockService = systemLockService;
        this.businessDateService = businessDateService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> getSystemLockStatus() {
        return ApiResponse.success("System lock status loaded successfully", status());
    }

    @PutMapping("/lock")
    public ApiResponse<Map<String, Object>> lockSystem() {
        systemLockService.lockSystem();

        return ApiResponse.success("System locked successfully", status());
    }

    @PutMapping("/unlock")
    public ApiResponse<Map<String, Object>> unlockSystem(
            @Valid @RequestBody EmergencyUnlockRequest request) {
        systemLockService.unlockSystem(request.reason());
        return ApiResponse.success("System unlocked successfully", status());
    }

    private Map<String, Object> status() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("locked", systemLockService.isSystemLocked());
        response.put("businessDate", businessDateService.getCurrentBusinessDate());
        String lockReason = systemLockService.isManuallyLocked()
                ? "Manual system lock"
                : systemLockService.isScheduledLockActive()
                ? "Scheduled settlement period" : null;
        response.put("lockReason", lockReason);
        response.put("lockStart", systemLockService.getLockStart());
        response.put("lockEnd", systemLockService.getLockEnd());
        response.put("emergencyUnlockActive", systemLockService.isEmergencyUnlockActive());
        response.put("emergencyUnlockExpiry", systemLockService.getEmergencyUnlockUntil());
        response.put("emergencyUnlockedBy", systemLockService.getEmergencyUnlockedBy());
        response.put("emergencyUnlockReason", systemLockService.getEmergencyUnlockReason());
        return response;
    }
}
