package com.casino.casinoerp.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CustomerKycUpdateRequest(
        @PastOrPresent(message = "Date of birth cannot be in the future")
        LocalDate dateOfBirth,

        @Size(max = 30, message = "Gender must not exceed 30 characters")
        String gender,

        @Size(max = 1000, message = "Permanent address must not exceed 1000 characters")
        String permanentAddress,

        @Size(max = 1000, message = "Current address must not exceed 1000 characters")
        String currentAddress,

        @Email(message = "Invalid email address")
        @Size(max = 254, message = "Email must not exceed 254 characters")
        String email,

        @Size(max = 150, message = "Occupation must not exceed 150 characters")
        String occupation
) {
}
