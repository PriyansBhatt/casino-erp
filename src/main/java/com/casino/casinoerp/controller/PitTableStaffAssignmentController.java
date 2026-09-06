package com.casino.casinoerp.controller;

import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.entity.PitTableStaffAssignmentRole;
import com.casino.casinoerp.service.PitTableStaffAssignmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/pit-tables")
public class PitTableStaffAssignmentController {
    private final PitTableStaffAssignmentService service;

    public PitTableStaffAssignmentController(PitTableStaffAssignmentService service) {
        this.service = service;
    }

    @GetMapping("/staff/candidates")
    public ApiResponse<List<PitStaffCandidateResponse>> candidates(@RequestParam PitTableStaffAssignmentRole role) {
        return ApiResponse.success("Pit staff candidates loaded successfully", service.getCandidates(role));
    }

    @GetMapping("/{tableId}/staff")
    public ApiResponse<List<PitTableStaffAssignmentResponse>> active(@PathVariable UUID tableId) {
        return ApiResponse.success("Active Pit Table staff loaded successfully", service.getActive(tableId));
    }

    @GetMapping("/{tableId}/staff/history")
    public ApiResponse<List<PitTableStaffAssignmentResponse>> history(@PathVariable UUID tableId) {
        return ApiResponse.success("Pit Table staff history loaded successfully", service.getHistory(tableId));
    }

    @PostMapping("/{tableId}/staff")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PitTableStaffAssignmentResponse> assign(
            @PathVariable UUID tableId, @Valid @RequestBody AssignPitTableStaffRequest request) {
        return ApiResponse.success("Pit Table staff assigned successfully", service.assign(tableId, request));
    }

    @PostMapping("/{tableId}/staff/{assignmentId}/end")
    public ApiResponse<PitTableStaffAssignmentResponse> end(
            @PathVariable UUID tableId, @PathVariable UUID assignmentId,
            @Valid @RequestBody EndPitTableStaffAssignmentRequest request) {
        return ApiResponse.success("Pit Table staff assignment ended successfully",
                service.end(tableId, assignmentId, request));
    }

    @PostMapping("/{tableId}/staff/{assignmentRole}/handover")
    public ApiResponse<PitTableStaffAssignmentResponse> handover(
            @PathVariable UUID tableId, @PathVariable PitTableStaffAssignmentRole assignmentRole,
            @Valid @RequestBody HandoverPitTableStaffRequest request) {
        return ApiResponse.success("Pit Table staff handover completed successfully",
                service.handover(tableId, assignmentRole, request));
    }
}
