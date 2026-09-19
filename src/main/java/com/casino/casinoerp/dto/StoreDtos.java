package com.casino.casinoerp.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public final class StoreDtos {
    private StoreDtos() {}
    public enum Unit { PCS, BOX, PACK, BOTTLE }
    public enum MovementType { OPENING, RECEIPT, ISSUE, ADJUSTMENT_IN, ADJUSTMENT_OUT }
    public record ItemWrite(@NotBlank @Size(max=50) String code, @NotBlank @Size(max=150) String name,
        @NotBlank @Size(max=100) String category, @NotNull Unit unit, @NotNull Boolean active, @PositiveOrZero Long expectedVersion) {
        @com.fasterxml.jackson.annotation.JsonAnySetter public void unknown(String name,Object value) { throw new IllegalArgumentException("Unsupported Store request field."); }
    }
    public record RequestLine(@NotNull UUID itemId,
        @NotNull @Positive @JsonDeserialize(using=StrictStoreQuantityDeserializer.class) Integer quantity) {
        @com.fasterxml.jackson.annotation.JsonAnySetter public void unknown(String name,Object value) { throw new IllegalArgumentException("Unsupported Store request field."); }
    }
    public record RequestCreate(@NotNull UUID staffProfileId, LocalDate requiredDate, @Size(max=1000) String remarks,
        @NotEmpty @Size(max=50) List<@Valid RequestLine> lines, @NotBlank @Size(max=100) String idempotencyKey) {
        @com.fasterxml.jackson.annotation.JsonAnySetter public void unknown(String name,Object value) { throw new IllegalArgumentException("Unsupported Store request field."); }
    }
    public record Quantity(@NotNull @Positive @JsonDeserialize(using=StrictStoreQuantityDeserializer.class) Integer quantity,
        @NotBlank @Size(max=100) String idempotencyKey, @Size(max=200) String externalReference) {
        @com.fasterxml.jackson.annotation.JsonAnySetter public void unknown(String name,Object value) { throw new IllegalArgumentException("Unsupported Store request field."); }
    }
    public record Adjustment(@NotNull MovementType type,
        @NotNull @Positive @JsonDeserialize(using=StrictStoreQuantityDeserializer.class) Integer quantity,
        @NotBlank @Size(max=500) String reason, @NotBlank @Size(max=100) String idempotencyKey) {
        @com.fasterxml.jackson.annotation.JsonAnySetter public void unknown(String name,Object value) { throw new IllegalArgumentException("Unsupported Store request field."); }
    }
    public record Transition(@NotNull @PositiveOrZero Long expectedVersion, @Size(max=500) String reason,
        @Size(max=200) String supplierReference) {
        @com.fasterxml.jackson.annotation.JsonAnySetter public void unknown(String name,Object value) { throw new IllegalArgumentException("Unsupported Store request field."); }
    }
    public record Receipt(UUID id, String reference) {}
    public record Page<T>(List<T> items, int page, int size, boolean hasNext) {}
    public record Item(UUID id, String code, String name, String category, String unit, boolean active,
        int quantityBalance, long version, boolean hasHistory, boolean hasMovements, LocalDateTime createdAt) {}
    public record Staff(UUID id, String employeeCode, String name, UUID departmentId, String departmentName) {}
    public record Request(UUID id, String reference, UUID departmentId, String departmentName, UUID requesterStaffProfileId,
        String requesterName, UUID recordedByUserId, String recordedByName, LocalDateTime createdAt, LocalDate requiredDate,
        String remarks, String status, long version, LocalDateTime cancelledAt, String cancellationReason) {}
    public record Line(UUID id, UUID itemId, String itemCode, String itemName, String unit, boolean active,
        int requestedQuantity, int issuedQuantity, int cancelledQuantity, int outstandingQuantity, int availableQuantity,
        UUID procurementId, String procurementReference, String procurementStatus) {}
    public record Detail(Request request, List<Line> lines) {}
    public record Procurement(UUID id, String reference, UUID requestLineId, UUID requestId, String requestReference,
        String departmentName, UUID itemId, String itemCode, String itemName, String unit, int quantity, int receivedQuantity,
        int outstandingQuantity, String status, String supplierReference, LocalDateTime createdAt, LocalDateTime orderedAt,
        LocalDateTime cancelledAt, String cancellationReason, long version) {}
    public record Movement(UUID id, String reference, UUID itemId, String itemCode, String itemName, String unit,
        String movementType, int quantity, int balanceAfter, UUID requestLineId, String requestReference,
        UUID procurementId, String procurementReference, String departmentName, UUID performedBy, String performedByName,
        LocalDateTime performedAt, LocalDate businessDate, String reason, String externalReference) {}
}
