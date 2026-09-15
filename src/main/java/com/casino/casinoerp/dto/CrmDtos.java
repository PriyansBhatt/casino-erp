package com.casino.casinoerp.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
public final class CrmDtos {
 private CrmDtos() {}
 public enum ServiceType { GIFT, FOOD, TICKET, OTHER }
 public enum TransportType { AIRPORT_PICKUP, AIRPORT_DROP, LOCAL_TRAVEL, OTHER }
 public enum Classification { COMPLIMENTARY, CUSTOMER_PAID, UNSPECIFIED }
 public record ServiceCreate(@NotNull UUID customerId, UUID customerSessionId,
  @NotNull ServiceType serviceType, @NotBlank @Size(max=2000) String description,
  @NotNull LocalDateTime serviceAt, @DecimalMin("0") @Digits(integer=17,fraction=2) BigDecimal cost,
  @NotNull Classification classification, @Size(max=2000) String notes,
  @NotBlank @Size(max=100) String idempotencyKey) {
 @com.fasterxml.jackson.annotation.JsonAnySetter public void rejectUnknown(String key,Object value){throw new IllegalArgumentException("Unsupported field: "+key);}
 }
 public record TransportCreate(@NotNull UUID customerId, UUID customerSessionId,
  @NotNull TransportType transportType, @NotBlank @Size(max=300) String pickup,
  @NotBlank @Size(max=300) String destination, @NotNull LocalDateTime scheduledAt,
  @Size(max=300) String vehicle, @Size(max=300) String driver,
  @DecimalMin("0") @Digits(integer=17,fraction=2) BigDecimal cost, @Size(max=2000) String notes,
  @NotBlank @Size(max=100) String idempotencyKey) {
 @com.fasterxml.jackson.annotation.JsonAnySetter public void rejectUnknown(String key,Object value){throw new IllegalArgumentException("Unsupported field: "+key);}
 }
 public record Change(@NotBlank String status,@NotNull @Min(0) Long expectedVersion) {}
 public record History(LocalDate recordingBusinessDate,String businessDateStatus,int limit,List<Map<String,Object>> records) {}
}
