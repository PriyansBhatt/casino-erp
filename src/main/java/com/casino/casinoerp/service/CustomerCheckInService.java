package com.casino.casinoerp.service;

import com.casino.casinoerp.entity.CustomerCheckIn;
import com.casino.casinoerp.repository.CustomerCheckInRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.casino.casinoerp.service.BusinessDateValidationService;

import java.util.List;

@Service
public class CustomerCheckInService {

    private final CustomerCheckInRepository customerCheckInRepository;
    private final BusinessDateService businessDateService;
    private final SystemLockService systemLockService;
    private final AuditLogService auditLogService;
    private final RolePermissionService rolePermissionService;
    private final CurrentUserRoleService currentUserRoleService;
    private final BusinessDateValidationService businessDateValidationService;

    public CustomerCheckInService(
            CustomerCheckInRepository customerCheckInRepository,
            BusinessDateService businessDateService,
            SystemLockService systemLockService,
            AuditLogService auditLogService,
            RolePermissionService rolePermissionService,
            CurrentUserRoleService currentUserRoleService,
            BusinessDateValidationService businessDateValidationService) {

        this.customerCheckInRepository = customerCheckInRepository;
        this.businessDateService = businessDateService;
        this.systemLockService = systemLockService;
        this.auditLogService = auditLogService;
        this.rolePermissionService = rolePermissionService;
        this.currentUserRoleService = currentUserRoleService;
        this.businessDateValidationService = businessDateValidationService;
    }

    public List<CustomerCheckIn> getAllCheckIns() {
        return customerCheckInRepository.findAll();
    }

    @Transactional
    public CustomerCheckIn save(CustomerCheckIn checkIn) {

        businessDateService.validateNewOperationalMutationAllowed();
        businessDateService.validateBusinessDateIsOpen();

        String role = currentUserRoleService.getCurrentUserRole();

        if (!role.equalsIgnoreCase("Receptionist")
                && !role.equalsIgnoreCase("Reception")
                && !role.equalsIgnoreCase("Super Admin")
                && !role.equalsIgnoreCase("SUPER_ADMIN")) {

            throw new RuntimeException(
                    "Access denied. Only Receptionist or Super Admin can check in customers."
            );
        }

        if (systemLockService.isSystemLocked()) {
            throw new RuntimeException(
                    "System is locked. Customer check-in is not allowed."
            );
        }

        checkIn.setBusinessDate(
                businessDateService.getCurrentBusinessDate()
        );

        CustomerCheckIn saved = customerCheckInRepository.save(checkIn);

        auditLogService.log(
                "CHECK_IN",
                "CUSTOMER_CHECKIN",
                saved.getId(),
                currentUserRoleService.getCurrentUserId(),
                "Customer checked in"
        );

        return saved;
    }
}
