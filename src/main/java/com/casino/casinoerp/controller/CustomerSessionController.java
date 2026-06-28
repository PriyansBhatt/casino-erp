package com.casino.casinoerp.controller;

import com.casino.casinoerp.entity.CustomerSession;
import com.casino.casinoerp.service.CustomerSessionService;
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
    public CustomerSession createSession(@RequestBody CustomerSession session) {
        return customerSessionService.save(session);
    }

    @PostMapping("/{sessionId}/close")
    public CustomerSession closeSession(@PathVariable java.util.UUID sessionId) {
        return customerSessionService.closeSession(sessionId);
    }

    @GetMapping
    public List<CustomerSession> getAllSessions() {
        return customerSessionService.getAllSessions();
    }
}