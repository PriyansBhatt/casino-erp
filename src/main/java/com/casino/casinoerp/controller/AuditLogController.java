package com.casino.casinoerp.controller;

import com.casino.casinoerp.dto.ApiResponse;
import com.casino.casinoerp.entity.AuditLog;
import com.casino.casinoerp.service.AuditLogService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    private final AuditLogService service;

    public AuditLogController(AuditLogService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<AuditLog>> getAll() {
        return ApiResponse.success(
                "Audit logs loaded successfully",
                service.getAll()
        );
    }

    @GetMapping("/user/{userId}")
    public ApiResponse<List<AuditLog>> getByUser(@PathVariable UUID userId) {
        return ApiResponse.success(
                "Audit logs loaded by user",
                service.getByUser(userId)
        );
    }

    @GetMapping("/action/{action}")
    public ApiResponse<List<AuditLog>> getByAction(@PathVariable String action) {
        return ApiResponse.success(
                "Audit logs loaded by action",
                service.getByAction(action)
        );
    }

    @GetMapping("/module/{module}")
    public ApiResponse<List<AuditLog>> getByModule(@PathVariable String module) {
        return ApiResponse.success(
                "Audit logs loaded by module",
                service.getByModule(module)
        );
    }

    @GetMapping("/business-date/{businessDate}")
    public ApiResponse<List<AuditLog>> getByBusinessDate(@PathVariable LocalDate businessDate) {
        return ApiResponse.success(
                "Audit logs loaded by business date",
                service.getByBusinessDate(businessDate)
        );
    }
}