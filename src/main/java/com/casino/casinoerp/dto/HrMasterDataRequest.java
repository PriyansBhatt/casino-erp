package com.casino.casinoerp.dto;

import jakarta.validation.constraints.*;

public record HrMasterDataRequest(
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 1000) String description,
        Boolean active,
        Integer sortOrder
) {}
