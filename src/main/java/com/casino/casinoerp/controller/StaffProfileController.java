package com.casino.casinoerp.controller;

import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.service.StaffProfileService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/hr/staff")
public class StaffProfileController {
    private final StaffProfileService service;
    public StaffProfileController(StaffProfileService service){this.service=service;}
    @GetMapping public ApiResponse<List<StaffProfileResponse>> list(){return ApiResponse.success("Staff profiles loaded successfully",service.list());}
    @GetMapping("/me") public ApiResponse<StaffProfileResponse> me(){return ApiResponse.success("Staff profile loaded successfully",service.me());}
    @GetMapping("/candidates") public ApiResponse<List<HrStaffUserCandidateResponse>> candidates(){return ApiResponse.success("Staff Profile user candidates loaded successfully",service.candidates());}
    @GetMapping("/{id}") public ApiResponse<StaffProfileResponse> get(@PathVariable UUID id){return ApiResponse.success("Staff profile loaded successfully",service.get(id));}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public ApiResponse<StaffProfileResponse> create(@Valid @RequestBody CreateStaffProfileRequest request){return ApiResponse.success("Staff profile created successfully",service.create(request));}
    @PatchMapping("/{id}") public ApiResponse<StaffProfileResponse> update(@PathVariable UUID id,@Valid @RequestBody UpdateStaffProfileRequest request){return ApiResponse.success("Staff profile updated successfully",service.update(id,request));}
}
