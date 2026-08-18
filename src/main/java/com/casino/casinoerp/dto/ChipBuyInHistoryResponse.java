package com.casino.casinoerp.dto;

public record ChipBuyInHistoryResponse(
        ChipBuyInResponse transaction,
        String customerCode,
        String customerName
) {}
