package com.casino.casinoerp.dto;
import jakarta.validation.constraints.*;
public record ChangeUserStatusRequest(@NotBlank String status, @NotBlank String expectedStatus) {
    @Override public String toString() { return "ChangeUserStatusRequest[redacted]"; }
}
