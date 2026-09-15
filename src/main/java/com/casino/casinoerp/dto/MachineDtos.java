package com.casino.casinoerp.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class MachineDtos {
    private MachineDtos() {}
    public enum Type { SLOT, AUTOMATIC_ROULETTE }
    public enum Availability { AVAILABLE, OUT_OF_SERVICE }
    public record Create(@NotBlank @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9_-]{0,39}") String machineCode,
            @NotBlank @Size(max=120) String displayName, @NotNull Type machineType,
            @Size(max=120) String location, @NotNull LocalDate expectedBusinessDate) {}
    public record ChangeStatus(@NotNull Availability status, @NotNull Availability expectedStatus,
            @NotNull LocalDate expectedBusinessDate) {}
    public record Start(@NotNull UUID customerId, @NotNull UUID customerSessionId,
            @NotNull LocalDate expectedBusinessDate, @NotBlank @Size(max=100) String idempotencyKey) {}
    /** Expected date is the original play Business Date, retained even after rollover. */
    public record End(@NotNull LocalDate expectedBusinessDate, @NotBlank @Size(max=100) String idempotencyKey) {}
    public record Candidate(UUID customerId, String customerCode, String customerName, UUID customerSessionId,
            String sessionCode, LocalDate businessDate) {}
    public record Receipt(UUID machineId, UUID playId, LocalDate businessDate) {}
    public record Play(UUID id, UUID machineId, UUID customerId, String customerCode, String customerName,
            UUID customerSessionId, String sessionCode, LocalDate businessDate, String status,
            LocalDateTime startedAt, LocalDateTime endedAt, UUID startedBy, UUID endedBy) {}
    public record Machine(UUID id, String machineCode, String displayName, Type machineType, String location,
            String operationalStatus, LocalDateTime createdAt, LocalDateTime updatedAt, Play activePlay) {}
    public record Overview(LocalDate businessDate, String businessDateStatus, List<Machine> machines) {}
    public record Detail(Machine machine, List<Play> recentPlays, int historyLimit) {}
}
