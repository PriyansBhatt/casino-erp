package com.casino.casinoerp.service;

import com.casino.casinoerp.entity.CustomerSession;
import com.casino.casinoerp.entity.PitTable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class BusinessDateValidationService {

    private final CustomerSessionService customerSessionService;
    private final PitTableService pitTableService;

    public BusinessDateValidationService(
            CustomerSessionService customerSessionService,
            PitTableService pitTableService) {

        this.customerSessionService = customerSessionService;
        this.pitTableService = pitTableService;
    }

    public List<String> validateCloseRequirements() {

        List<String> errors = new ArrayList<>();

        long openSessions = customerSessionService.getAllSessions()
                .stream()
                .filter(s -> "OPEN".equalsIgnoreCase(s.getStatus()))
                .count();

        if (openSessions > 0) {
            errors.add(openSessions + " customer session(s) still OPEN");
        }

        long unresolvedChipExposure = customerSessionService.getAllSessions()
                .stream()
                .filter(s -> s.getUnresolvedChipExposure() != null)
                .filter(s -> s.getUnresolvedChipExposure().compareTo(BigDecimal.ZERO) > 0)
                .count();

        if (unresolvedChipExposure > 0) {
            errors.add(unresolvedChipExposure + " customer session(s) have unresolved chip exposure");
        }

        long openTables = pitTableService.getAllTables()
                .stream()
                .filter(t -> "OPEN".equalsIgnoreCase(t.getStatus()))
                .count();

        if (openTables > 0) {
            errors.add(openTables + " pit table(s) still OPEN");
        }

        return errors;
    }
}