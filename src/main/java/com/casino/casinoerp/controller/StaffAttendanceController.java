package com.casino.casinoerp.controller;

import com.casino.casinoerp.dto.ApiResponse;
import com.casino.casinoerp.dto.StaffAttendanceResponse;
import com.casino.casinoerp.service.StaffAttendanceService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/attendance")
public class StaffAttendanceController {
    private final StaffAttendanceService service;

    public StaffAttendanceController(StaffAttendanceService service) {
        this.service = service;
    }

    @PostMapping("/check-in")
    public ApiResponse<StaffAttendanceResponse> checkIn() {
        return ApiResponse.success("Attendance check-in recorded successfully", service.checkIn());
    }

    @PostMapping("/check-out")
    public ApiResponse<StaffAttendanceResponse> checkOut() {
        return ApiResponse.success("Attendance check-out recorded successfully", service.checkOut());
    }

    @GetMapping("/current")
    public ApiResponse<StaffAttendanceResponse> current() {
        return ApiResponse.success("Current attendance loaded successfully", service.current());
    }

    @GetMapping("/me")
    public ApiResponse<List<StaffAttendanceResponse>> mine(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        return ApiResponse.success("Attendance history loaded successfully", service.myHistory(businessDate));
    }

    @GetMapping
    public ApiResponse<List<StaffAttendanceResponse>> report(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        return ApiResponse.success("Business Date attendance report loaded successfully",
                service.report(businessDate));
    }
}
