package com.casino.casinoerp.service;

import com.casino.casinoerp.entity.CustomerServiceRecord;
import com.casino.casinoerp.repository.CustomerServiceRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class CustomerServiceRecordService {

    private final CustomerServiceRecordRepository repository;
    private final BusinessDateService businessDateService;
    private final SystemLockService systemLockService;
    private final AuditLogService auditLogService;
    private final CurrentUserRoleService currentUserRoleService;

    public CustomerServiceRecordService(
            CustomerServiceRecordRepository repository,
            BusinessDateService businessDateService,
            SystemLockService systemLockService,
            AuditLogService auditLogService,
            CurrentUserRoleService currentUserRoleService) {

        this.repository = repository;
        this.businessDateService = businessDateService;
        this.systemLockService = systemLockService;
        this.auditLogService = auditLogService;
        this.currentUserRoleService = currentUserRoleService;
    }

    @Transactional
    public CustomerServiceRecord save(CustomerServiceRecord record) {

        if(record.getId()!=null) throw new IllegalArgumentException("New service records must not supply an ID.");
        businessDateService.validateNewOperationalMutationAllowed();
        businessDateService.validateBusinessDateIsOpen();
        
        String role = currentUserRoleService.getCurrentUserRole();

        if (!role.equalsIgnoreCase("Receptionist")
                && !role.equalsIgnoreCase("Reception")
                && !role.equalsIgnoreCase("Manager")
                && !role.equalsIgnoreCase("Super Admin")
                && !role.equalsIgnoreCase("SUPER_ADMIN")) {

            throw new RuntimeException(
                    "Access denied. Only Receptionist, Manager or Super Admin can create service records."
            );
        }

        if (systemLockService.isSystemLocked()) {
            throw new RuntimeException(
                    "System is locked. Customer service records are not allowed."
            );
        }

        record.setBusinessDate(businessDateService.getCurrentBusinessDate());
        record.setCreatedAt(LocalDateTime.now());

        CustomerServiceRecord saved = repository.save(record);

        auditLogService.log(
                "CREATE_SERVICE_RECORD",
                "CUSTOMER_SERVICE",
                saved.getId(),
                currentUserRoleService.getCurrentUserId(),
                "Customer service record created"
        );

        return saved;
    }

    public List<CustomerServiceRecord> getByBusinessDate(java.time.LocalDate businessDate) {
        return repository.findByBusinessDate(businessDate);
    }

    public List<CustomerServiceRecord> getAll() {
        return repository.findAll();
    }

    public List<CustomerServiceRecord> getByCustomer(UUID customerId) {
        return repository.findByCustomerId(customerId);
    }

    public List<CustomerServiceRecord> getBySession(UUID sessionId) {
        return repository.findByCustomerSessionId(sessionId);
    }
}
