package com.casino.casinoerp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerRegistrationRequest(
        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 150, message = "Full name must be between 2 and 150 characters")
        String fullName,

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^[0-9+\\- ]{7,20}$", message = "Invalid phone number")
        String phone,

        @NotBlank(message = "Nationality is required")
        @Size(max = 100, message = "Nationality must not exceed 100 characters")
        String nationality
) {
}
