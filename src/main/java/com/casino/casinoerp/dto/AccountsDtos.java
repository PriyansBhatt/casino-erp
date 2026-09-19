package com.casino.casinoerp.dto;

import java.math.BigDecimal;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class AccountsDtos {
    private AccountsDtos() {}
    public record Line(String description, @JsonSerialize(using=ToStringSerializer.class) BigDecimal amount) {}
    public record Source(String type, UUID id) {}
    public record SourceSnapshot(String type, UUID id, String reference) {}
    public record Invoice(UUID partyId, String invoiceReference, LocalDate invoiceDate, LocalDate dueDate,
            String currency, @JsonSerialize(using=ToStringSerializer.class) BigDecimal subtotal, @JsonSerialize(using=ToStringSerializer.class) BigDecimal discount, @JsonSerialize(using=ToStringSerializer.class) BigDecimal tax, @JsonSerialize(using=ToStringSerializer.class) BigDecimal total,
            List<Line> lines, List<Source> sources) {
        @com.fasterxml.jackson.annotation.JsonAnySetter public void unknown(String name,Object value) {throw new IllegalArgumentException("Unsupported invoice field.");}
    }
    public record BillWrite(String idempotencyKey, Long expectedVersion, LocalDate expectedBusinessDate, Invoice invoice) {
        @com.fasterxml.jackson.annotation.JsonAnySetter public void unknown(String name,Object value) {throw new IllegalArgumentException("Unsupported bill field.");}
    }
    public record Action(String idempotencyKey, Long expectedVersion, String reason) {
        @com.fasterxml.jackson.annotation.JsonAnySetter public void unknown(String name,Object value) {throw new IllegalArgumentException("Unsupported decision field.");}
    }
    public record PartyWrite(String idempotencyKey, String code, String name, String kind) {}
    public record Snapshot(Invoice invoice, String partyCode, String partyName, String partyKind,
            List<SourceSnapshot> sourceSnapshots, List<UUID> evidenceIds) {}
    public record Receipt(UUID id, long version, UUID operationId) {}
    public record Page<T>(List<T> items, boolean hasMore, int page, int size) {}
}
