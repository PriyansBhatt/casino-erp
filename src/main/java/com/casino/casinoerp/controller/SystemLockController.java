package com.casino.casinoerp.controller;

import com.casino.casinoerp.dto.ApiResponse;
import com.casino.casinoerp.service.SystemLockService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/system-lock")
public class SystemLockController {

    private final SystemLockService systemLockService;

    public SystemLockController(SystemLockService systemLockService) {
        this.systemLockService = systemLockService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> getSystemLockStatus() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("locked", systemLockService.isSystemLocked());
        response.put("lockStart", "06:30");
        response.put("lockEnd", "12:30");

        return ApiResponse.success("System lock status loaded successfully", response);
    }

    @PutMapping("/lock")
    public ApiResponse<Map<String, Object>> lockSystem() {
        systemLockService.lockSystem();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("locked", systemLockService.isSystemLocked());

        return ApiResponse.success("System locked successfully", response);
    }

    @PutMapping("/unlock")
    public ApiResponse<Map<String, Object>> unlockSystem() {
        systemLockService.unlockSystem();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("locked", systemLockService.isSystemLocked());

        return ApiResponse.success("System unlocked successfully", response);
    }
}