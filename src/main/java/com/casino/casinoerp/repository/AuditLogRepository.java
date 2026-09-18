package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    long countByBusinessDate(LocalDate businessDate);
}
