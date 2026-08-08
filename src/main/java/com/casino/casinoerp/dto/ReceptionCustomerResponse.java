package com.casino.casinoerp.dto;

import java.util.UUID;

public record ReceptionCustomerResponse(
        UUID id,
        String customerCode,
        String fullName,
        String phone,
        String nationality,
        String status
) {
}
