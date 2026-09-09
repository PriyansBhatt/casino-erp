package com.casino.casinoerp.dto;

import jakarta.validation.constraints.*;

public record HrMasterDataUpdateRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 1000) String description,
        @NotNull Boolean active,
        Integer sortOrder
) {}
