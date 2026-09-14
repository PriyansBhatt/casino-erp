package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.ChipControlDirectoryResponse;
import com.casino.casinoerp.repository.ChipControlReadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChipControlService {
    private final ChipControlReadRepository reads;
    private final BusinessDateService businessDates;
    private final CurrentUserRoleService roles;
    private final RolePermissionService permissions;

    public ChipControlService(ChipControlReadRepository reads, BusinessDateService businessDates,
            CurrentUserRoleService roles, RolePermissionService permissions) {
        this.reads = reads; this.businessDates = businessDates; this.roles = roles; this.permissions = permissions;
    }

    @Transactional(readOnly = true)
    public ChipControlDirectoryResponse getCurrentOpenSessionPositions() {
        if (!roles.getCurrentRole().map(permissions::canViewChipControl).orElse(false)) {
            throw new RuntimeException("Access denied. Chip Control financial data is restricted.");
        }
        var date = businessDates.getCurrentOpenBusinessDate()
                .orElseThrow(() -> new IllegalStateException("Current business date is not opened."))
                .getBusinessDate();
        return new ChipControlDirectoryResponse(date, reads.directory(date));
    }
}
