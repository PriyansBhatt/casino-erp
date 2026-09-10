package com.casino.casinoerp.controller;

import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.entity.LeaveRequestStatus;
import com.casino.casinoerp.service.StaffLeaveRequestService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/hr/leave")
public class StaffLeaveRequestController {
    private final StaffLeaveRequestService service;
    public StaffLeaveRequestController(StaffLeaveRequestService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<StaffLeaveRequestResponse> create(
            @Valid @RequestBody CreateStaffLeaveRequest request) {
        return ApiResponse.success("Leave Request submitted successfully", service.create(request));
    }

    @GetMapping("/me")
    public ApiResponse<List<StaffLeaveRequestResponse>> mine(
            @RequestParam(required = false) LeaveRequestStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ApiResponse.success("Leave Request history loaded successfully",
                service.mine(status, startDate, endDate));
    }

    @GetMapping
    public ApiResponse<List<StaffLeaveRequestResponse>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) UUID staffProfileId,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID leaveTypeId,
            @RequestParam(required = false) LeaveRequestStatus status) {
        return ApiResponse.success("Leave Requests loaded successfully",
                service.list(startDate, endDate, staffProfileId, departmentId, leaveTypeId, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<StaffLeaveRequestResponse> get(@PathVariable UUID id) {
        return ApiResponse.success("Leave Request loaded successfully", service.get(id));
    }
}
