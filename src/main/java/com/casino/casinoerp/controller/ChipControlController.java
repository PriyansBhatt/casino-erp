package com.casino.casinoerp.controller;

import com.casino.casinoerp.dto.ApiResponse;
import com.casino.casinoerp.dto.ChipControlDirectoryResponse;
import com.casino.casinoerp.service.ChipControlService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chip-control")
public class ChipControlController {
    private final ChipControlService service;

    public ChipControlController(ChipControlService service) {
        this.service = service;
    }

    @GetMapping("/sessions")
    public ApiResponse<ChipControlDirectoryResponse> getCurrentOpenSessionPositions() {
        return ApiResponse.success("Chip Control positions loaded successfully",
                service.getCurrentOpenSessionPositions());
    }
}
