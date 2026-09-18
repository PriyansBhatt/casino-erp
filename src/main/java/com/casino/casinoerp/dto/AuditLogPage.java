package com.casino.casinoerp.dto;
import java.util.List;
public record AuditLogPage(List<AuditLogResponse> items, int page, int size, boolean hasNext) {}
