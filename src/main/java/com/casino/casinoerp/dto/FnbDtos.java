package com.casino.casinoerp.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public final class FnbDtos {
    private FnbDtos() {}
    public enum Type { FOOD, BEVERAGE }
    public enum Status { PENDING, PREPARING, READY, DELIVERED, CANCELLED }
    public record Create(@NotNull UUID customerId, UUID customerSessionId,
            @NotNull LocalDate expectedBusinessDate, @NotNull Type type,
            @NotBlank @Size(max=200) String item,
            @NotNull @Min(1) @JsonDeserialize(using=StrictFnbQuantityDeserializer.class) Integer quantity,
            @NotBlank @Size(max=300) String location, @Size(max=2000) String remarks,
            @NotBlank @Size(max=100) String idempotencyKey) {
        @JsonAnySetter public void unknown(String name,Object value) { throw new IllegalArgumentException("Unsupported F&B field: " + name); }
    }
    public record Change(@NotNull Status status, @NotNull @Min(0) @Max(2147483646) Integer expectedVersion) {
        @JsonAnySetter public void unknown(String name,Object value) { throw new IllegalArgumentException("Unsupported F&B field: " + name); }
    }
    public record Request(UUID id, LocalDate businessDate, LocalDateTime requestedAt, LocalDateTime deliveredAt, LocalDate deliveredBusinessDate,
            UUID customerId, String customerCode, String customerName, UUID customerSessionId,
            Type type, String item, int quantity, String location, String department, Status status,
            UUID requestedBy, String requester, UUID handledBy, String handler, String remarks, int version) {}
    public record Page(LocalDate currentBusinessDate, String businessDateStatus, int limit, List<Request> records) {}
    public record Overview(LocalDate businessDate, String businessDateStatus, boolean available,
            Map<String,Long> metrics) {}
}
