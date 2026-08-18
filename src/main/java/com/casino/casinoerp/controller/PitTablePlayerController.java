package com.casino.casinoerp.controller;

import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.service.PitTableCustomerAssignmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/pit/tables/{tableId}/players")
public class PitTablePlayerController {
    private final PitTableCustomerAssignmentService service;
    public PitTablePlayerController(PitTableCustomerAssignmentService service) { this.service = service; }

    @GetMapping("/history")
    public ApiResponse<List<PitTablePlayerResponse>> history(
            @PathVariable UUID tableId) {
        return ApiResponse.success("Pit Table player history loaded successfully", service.getPlayerHistory(tableId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PitTablePlayerResponse> assign(
            @PathVariable UUID tableId, @Valid @RequestBody AssignPitTableCustomerRequest request) {
        return ApiResponse.success("Customer assigned to Pit Table successfully", service.assign(tableId, request));
    }

    @GetMapping
    public ApiResponse<List<PitTablePlayerResponse>> players(@PathVariable UUID tableId) {
        return ApiResponse.success("Active Pit Table players loaded successfully", service.getActivePlayers(tableId));
    }

    @PostMapping("/{assignmentId}/leave")
    public ApiResponse<PitTablePlayerResponse> leave(
            @PathVariable UUID tableId, @PathVariable UUID assignmentId) {
        return ApiResponse.success("Customer left Pit Table successfully", service.leave(tableId, assignmentId));
    }
}
