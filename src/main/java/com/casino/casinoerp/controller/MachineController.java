package com.casino.casinoerp.controller;

import com.casino.casinoerp.dto.ApiResponse;
import com.casino.casinoerp.dto.MachineDtos.*;
import com.casino.casinoerp.service.MachineService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/machines")
public class MachineController {
    private final MachineService service;
    public MachineController(MachineService service) { this.service=service; }
    @GetMapping public ApiResponse<Overview> overview() { return ApiResponse.success("Machines loaded",service.overview()); }
    @GetMapping("/eligible-players") public ApiResponse<List<Candidate>> candidates(@RequestParam String query) { return ApiResponse.success("Eligible unexited sessions",service.candidates(query)); }
    @GetMapping("/{id}") public ApiResponse<Detail> detail(@PathVariable UUID id) { return ApiResponse.success("Machine detail",service.detail(id)); }
    @PostMapping public ApiResponse<Machine> create(@Valid @RequestBody Create request) { return ApiResponse.success("Machine created",service.create(request)); }
    @PostMapping("/{id}/status") public ApiResponse<Machine> status(@PathVariable UUID id,@Valid @RequestBody ChangeStatus request) { return ApiResponse.success("Availability changed",service.status(id,request)); }
    @PostMapping("/{id}/plays") public ApiResponse<Receipt> start(@PathVariable UUID id,@Valid @RequestBody Start request) { return ApiResponse.success("Play started",service.start(id,request)); }
    @PostMapping("/{id}/plays/{playId}/end") public ApiResponse<Receipt> end(@PathVariable UUID id,@PathVariable UUID playId,@Valid @RequestBody End request) { return ApiResponse.success("Play ended; no cash-out or casino exit",service.end(id,playId,request)); }
}
