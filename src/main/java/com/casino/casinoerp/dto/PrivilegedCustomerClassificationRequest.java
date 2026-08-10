package com.casino.casinoerp.dto;

import com.casino.casinoerp.entity.CustomerCategory;
import com.casino.casinoerp.entity.CustomerRiskLevel;
import jakarta.validation.constraints.Size;

public record PrivilegedCustomerClassificationRequest(
        CustomerCategory category,
        CustomerRiskLevel riskLevel,

        @Size(max = 4000, message = "Internal notes must not exceed 4000 characters")
        String internalNotes
) {
}
