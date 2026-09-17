package com.casino.casinoerp.dto;
import jakarta.validation.constraints.*;
public record ResetUserPasswordRequest(@com.fasterxml.jackson.annotation.JsonProperty(access=com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
        String password) {
    @Override public String toString() { return "ResetUserPasswordRequest[redacted]"; }
}
