package com.casino.casinoerp.controller;

import com.casino.casinoerp.dto.ApiResponse;
import com.casino.casinoerp.dto.FnbDtos.*;
import com.casino.casinoerp.service.FnbService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/fnb")
public class FnbController {
    private final FnbService service;
    public FnbController(FnbService service) { this.service=service; }
    @GetMapping("/requests") public ApiResponse<Page> history(@RequestParam(required=false) LocalDate businessDate,
            @RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,
            @RequestParam(defaultValue="") String type,@RequestParam(defaultValue="") String status,
            @RequestParam(defaultValue="") String q,@RequestParam(defaultValue="false") boolean live) {
        return ApiResponse.success("F&B requests",service.history(businessDate,from,to,type,status,q,live));
    }
    @GetMapping("/overview") public ApiResponse<Overview> overview() { return ApiResponse.success("F&B overview",service.overview()); }
    @GetMapping("/customers") public ApiResponse<List<Map<String,Object>>> customers(@RequestParam(defaultValue="") String q) { return ApiResponse.success("Customers",service.customers(q)); }
    @PostMapping("/requests") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String,Object>> create(@Valid @RequestBody Create request) { return ApiResponse.success("F&B request recorded",service.create(request)); }
    @PatchMapping("/requests/{id}/status") public ApiResponse<Map<String,Object>> change(@PathVariable UUID id,@Valid @RequestBody Change request) {
        return ApiResponse.success("F&B status recorded",service.change(id,request));
    }
}
