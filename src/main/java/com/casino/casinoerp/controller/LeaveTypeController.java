package com.casino.casinoerp.controller;

import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.service.LeaveTypeService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/hr/leave-types")
public class LeaveTypeController {
    private final LeaveTypeService service;
    public LeaveTypeController(LeaveTypeService service) { this.service = service; }

    @GetMapping
    public ApiResponse<List<LeaveTypeResponse>> list() {
        return ApiResponse.success("Leave Types loaded successfully", service.list());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<LeaveTypeResponse> create(@Valid @RequestBody CreateLeaveTypeRequest request) {
        return ApiResponse.success("Leave Type created successfully", service.create(request));
    }

    @PatchMapping("/{id}")
    public ApiResponse<LeaveTypeResponse> update(@PathVariable UUID id,
            @Valid @RequestBody UpdateLeaveTypeRequest request) {
        return ApiResponse.success("Leave Type updated successfully", service.update(id, request));
    }
}
