package com.casino.casinoerp.controller;
import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.service.AuditLogReadService;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {
    private final AuditLogReadService service;
    public AuditLogController(AuditLogReadService service) { this.service=service; }
    @GetMapping
    public ApiResponse<AuditLogPage> read(
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="50") int size,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate businessDate,
            @RequestParam(required=false) String action, @RequestParam(required=false) String module,
            @RequestParam(required=false) UUID actorId, @RequestParam(required=false) String search) {
        return ApiResponse.success("Audit records loaded", service.read(page,size,from,to,businessDate,action,module,actorId,search));
    }
    @GetMapping("/**")
    public org.springframework.http.ResponseEntity<ApiResponse<Object>> unsupportedRead() {
        return org.springframework.http.ResponseEntity.status(404)
                .body(ApiResponse.error("Audit endpoint not found. Use the bounded audit list."));
    }
}
