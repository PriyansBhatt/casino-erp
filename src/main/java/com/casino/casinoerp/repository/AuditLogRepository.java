package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    // AC1 records are visible only through its object-authorized history.
    @org.springframework.data.jpa.repository.Query(value="select count(*) from audit.audit_logs where business_date=:businessDate and coalesce(module_name,'') <> 'ACCOUNTS'", nativeQuery=true)
    long countByBusinessDate(LocalDate businessDate);
}
