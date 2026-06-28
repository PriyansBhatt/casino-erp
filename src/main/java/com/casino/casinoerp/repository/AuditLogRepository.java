package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByBusinessDate(LocalDate businessDate);

    List<AuditLog> findByModuleName(String moduleName);

    List<AuditLog> findByActionType(String actionType);

    List<AuditLog> findByPerformedBy(UUID performedBy);
}
