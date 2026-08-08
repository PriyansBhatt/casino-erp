package com.casino.casinoerp.controller;

import com.casino.casinoerp.dto.OpenCustomerSessionRequest;
import com.casino.casinoerp.dto.ReceptionSessionResponse;
import com.casino.casinoerp.service.CustomerSessionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sessions")
public class CustomerSessionController {

    private final CustomerSessionService customerSessionService;

    public CustomerSessionController(CustomerSessionService customerSessionService) {
        this.customerSessionService = customerSessionService;
    }

    @PostMapping
    public ReceptionSessionResponse createSession(
            @Valid @RequestBody OpenCustomerSessionRequest request
    ) {
        return customerSessionService.openSession(request.customerId());
    }

    @PostMapping("/{sessionId}/close")
    public ReceptionSessionResponse closeSession(@PathVariable java.util.UUID sessionId) {
        return customerSessionService.closeSession(sessionId);
    }

    @GetMapping
    public List<ReceptionSessionResponse> getAllSessions() {
        return customerSessionService.getAllReceptionSessions();
    }

    @GetMapping("/active/customer/{customerId}")
    public ReceptionSessionResponse getActiveSession(@PathVariable java.util.UUID customerId) {
        return customerSessionService.getActiveSession(customerId);
    }
}
