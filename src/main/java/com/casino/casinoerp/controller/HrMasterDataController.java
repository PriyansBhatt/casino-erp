package com.casino.casinoerp.controller;

import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.service.HrMasterDataService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/hr")
public class HrMasterDataController {
    private final HrMasterDataService service;
    public HrMasterDataController(HrMasterDataService service) { this.service = service; }

    @GetMapping("/departments") public ApiResponse<List<HrMasterDataResponse>> departments(){return ApiResponse.success("Departments loaded successfully",service.departments());}
    @PostMapping("/departments") @ResponseStatus(HttpStatus.CREATED) public ApiResponse<HrMasterDataResponse> createDepartment(@Valid @RequestBody HrMasterDataRequest request){return ApiResponse.success("Department created successfully",service.createDepartment(request));}
    @PatchMapping("/departments/{id}") public ApiResponse<HrMasterDataResponse> updateDepartment(@PathVariable UUID id,@Valid @RequestBody HrMasterDataUpdateRequest request){return ApiResponse.success("Department updated successfully",service.updateDepartment(id,request));}
    @GetMapping("/job-titles") public ApiResponse<List<HrMasterDataResponse>> jobTitles(){return ApiResponse.success("Job Titles loaded successfully",service.jobTitles());}
    @PostMapping("/job-titles") @ResponseStatus(HttpStatus.CREATED) public ApiResponse<HrMasterDataResponse> createJobTitle(@Valid @RequestBody HrMasterDataRequest request){return ApiResponse.success("Job Title created successfully",service.createJobTitle(request));}
    @PatchMapping("/job-titles/{id}") public ApiResponse<HrMasterDataResponse> updateJobTitle(@PathVariable UUID id,@Valid @RequestBody HrMasterDataUpdateRequest request){return ApiResponse.success("Job Title updated successfully",service.updateJobTitle(id,request));}
}
