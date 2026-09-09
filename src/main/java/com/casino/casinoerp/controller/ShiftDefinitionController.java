package com.casino.casinoerp.controller;

import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.service.ShiftDefinitionService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/hr/shifts")
public class ShiftDefinitionController {
    private final ShiftDefinitionService service;public ShiftDefinitionController(ShiftDefinitionService service){this.service=service;}
    @GetMapping public ApiResponse<List<ShiftDefinitionResponse>> list(){return ApiResponse.success("Shift Definitions loaded successfully",service.list());}
    @GetMapping("/{id}") public ApiResponse<ShiftDefinitionResponse> get(@PathVariable UUID id){return ApiResponse.success("Shift Definition loaded successfully",service.get(id));}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public ApiResponse<ShiftDefinitionResponse> create(@Valid @RequestBody CreateShiftDefinitionRequest request){return ApiResponse.success("Shift Definition created successfully",service.create(request));}
    @PatchMapping("/{id}") public ApiResponse<ShiftDefinitionResponse> update(@PathVariable UUID id,@Valid @RequestBody UpdateShiftDefinitionRequest request){return ApiResponse.success("Shift Definition updated successfully",service.update(id,request));}
}
