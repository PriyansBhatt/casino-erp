package com.casino.casinoerp.controller;

import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.service.HotelBookingService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/hotel-bookings")
public class HotelBookingController {
    private final HotelBookingService service; public HotelBookingController(HotelBookingService service){this.service=service;}
    @GetMapping("/current") public ApiResponse<List<HotelBookingResponse>> current(){return ApiResponse.success("Current hotel bookings loaded successfully",service.current());}
    @GetMapping("/{id}") public ApiResponse<HotelBookingResponse> get(@PathVariable UUID id){return ApiResponse.success("Hotel booking loaded successfully",service.get(id));}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public ApiResponse<HotelBookingResponse> create(@Valid @RequestBody CreateHotelBookingRequest request){return ApiResponse.success("Hotel booking requested successfully",service.create(request));}
    @PatchMapping("/{id}/approve") public ApiResponse<HotelBookingResponse> approve(@PathVariable UUID id){return ApiResponse.success("Hotel booking approved successfully",service.approve(id));}
    @PatchMapping("/{id}/reject") public ApiResponse<HotelBookingResponse> reject(@PathVariable UUID id){return ApiResponse.success("Hotel booking rejected successfully",service.reject(id));}
    @PatchMapping("/{id}/status") public ApiResponse<HotelBookingResponse> status(@PathVariable UUID id,@Valid @RequestBody UpdateHotelBookingStatusRequest request){return ApiResponse.success("Hotel booking status updated successfully",service.updateStatus(id,request));}
}
