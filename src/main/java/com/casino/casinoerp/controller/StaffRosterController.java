package com.casino.casinoerp.controller;

import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.entity.RosterStatus;
import com.casino.casinoerp.service.StaffRosterService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;

@RestController @RequestMapping("/api/hr/roster")
public class StaffRosterController {
    private final StaffRosterService service;public StaffRosterController(StaffRosterService service){this.service=service;}
    @GetMapping public ApiResponse<List<StaffRosterResponse>> list(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required=false) UUID staffProfileId,@RequestParam(required=false) UUID departmentId,
            @RequestParam(required=false) UUID shiftId,@RequestParam(required=false) RosterStatus status){return ApiResponse.success("Staff roster loaded successfully",service.list(fromDate,toDate,staffProfileId,departmentId,shiftId,status));}
    @GetMapping("/{id}") public ApiResponse<StaffRosterResponse> get(@PathVariable UUID id){return ApiResponse.success("Staff roster assignment loaded successfully",service.get(id));}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public ApiResponse<StaffRosterResponse> create(@Valid @RequestBody CreateStaffRosterRequest request){return ApiResponse.success("Staff roster assignment created successfully",service.create(request));}
    @PatchMapping("/{id}") public ApiResponse<StaffRosterResponse> update(@PathVariable UUID id,@Valid @RequestBody UpdateStaffRosterRequest request){return ApiResponse.success("Staff roster assignment updated successfully",service.update(id,request));}
    @PostMapping("/{id}/cancel") public ApiResponse<StaffRosterResponse> cancel(@PathVariable UUID id,@Valid @RequestBody CancelStaffRosterRequest request){return ApiResponse.success("Staff roster assignment cancelled successfully",service.cancel(id,request));}
}
