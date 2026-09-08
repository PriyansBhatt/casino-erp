package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.exception.*;
import com.casino.casinoerp.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CustomerBonusService {
    private final CustomerBonusRepository bonuses;
    private final CustomerRepository customers;
    private final CustomerSessionRepository sessions;
    private final BusinessDateRepository businessDates;
    private final BusinessDateService businessDateService;
    private final SystemLockService systemLock;
    private final AuthenticatedUserService authenticatedUsers;
    private final CurrentUserRoleService currentRoles;
    private final RolePermissionService permissions;
    private final UserRepository users;
    private final AuditLogService audit;

    public CustomerBonusService(CustomerBonusRepository bonuses, CustomerRepository customers,
            CustomerSessionRepository sessions, BusinessDateRepository businessDates,
            BusinessDateService businessDateService, SystemLockService systemLock,
            AuthenticatedUserService authenticatedUsers, CurrentUserRoleService currentRoles,
            RolePermissionService permissions, UserRepository users, AuditLogService audit) {
        this.bonuses = bonuses; this.customers = customers; this.sessions = sessions;
        this.businessDates = businessDates; this.businessDateService = businessDateService;
        this.systemLock = systemLock; this.authenticatedUsers = authenticatedUsers;
        this.currentRoles = currentRoles; this.permissions = permissions; this.users = users; this.audit = audit;
    }

    @Transactional
    public CustomerBonusResponse create(CreateCustomerBonusRequest request) {
        validateRole();
        String key = request.idempotencyKey().trim();
        CustomerBonus replay = bonuses.findByIdempotencyKey(key).orElse(null);
        if (replay != null) {
            validateReplay(replay, request);
            Customer customer = customers.findById(replay.getCustomerId()).orElse(null);
            CustomerSession session = sessions.findById(replay.getCustomerSessionId()).orElse(null);
            return response(replay, customer, session, null);
        }
        businessDateService.validateNewOperationalMutationAllowed();
        businessDateService.validateBusinessDateIsOpen();
        LocalDate date = businessDateService.getCurrentBusinessDate();
        if (systemLock.isSystemLocked()) {
            throw new ResourceConflictException("System is locked. Customer bonuses cannot be issued.");
        }
        Customer customer = customers.findById(request.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found."));
        if (customer.getStatus() != CustomerStatus.ACTIVE) {
            throw new IllegalArgumentException("Customer must be ACTIVE to receive a bonus.");
        }
        CustomerSession session = sessions.findById(request.customerSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer session not found."));
        if (!request.customerId().equals(session.getCustomerId())) {
            throw new IllegalArgumentException("Customer session does not belong to the supplied customer.");
        }
        if (!"OPEN".equalsIgnoreCase(session.getStatus()) || !date.equals(session.getBusinessDate())) {
            throw new IllegalArgumentException("Customer session must be OPEN for the current Business Date.");
        }
        User actor = authenticatedUsers.getRequiredUser();
        LocalDateTime now = LocalDateTime.now();
        CustomerBonus value = new CustomerBonus();
        value.setBonusCode("BON-" + date.toString().replace("-", "") + "-" + UUID.randomUUID());
        value.setCustomerId(customer.getId()); value.setCustomerSessionId(session.getId());
        value.setBusinessDate(date); value.setBonusType(request.bonusType()); value.setAmount(request.amount());
        value.setReason(request.reason().trim()); value.setStatus(CustomerBonusStatus.APPROVED);
        value.setCreatedBy(actor.getId()); value.setApprovedBy(actor.getId());
        value.setCreatedAt(now); value.setApprovedAt(now); value.setIdempotencyKey(key);
        CustomerBonus saved = bonuses.save(value);
        audit.log("CREATE_CUSTOMER_BONUS", "CUSTOMER_BONUS", saved.getId(), actor.getId(),
                "Bonus " + saved.getBonusCode() + ", businessDate=" + date + ", amount=" + saved.getAmount()
                        + ", type=" + saved.getBonusType() + ", status=APPROVED");
        return response(saved, customer, session, Map.of(actor.getId(), actor));
    }

    @Transactional(readOnly = true)
    public List<CustomerBonusResponse> getByBusinessDate(LocalDate requestedDate) {
        validateRole();
        LocalDate date = requestedDate == null
                ? businessDateService.getCurrentOpenBusinessDate()
                    .orElseThrow(() -> new IllegalStateException("Current business date is not opened."))
                    .getBusinessDate()
                : requestedDate;
        businessDates.findByBusinessDate(date)
                .orElseThrow(() -> new ResourceNotFoundException("Business Date not found."));
        List<CustomerBonus> values = bonuses.findByBusinessDateOrderByCreatedAtDesc(date);
        Map<UUID, Customer> customerMap = customers.findAllById(values.stream().map(CustomerBonus::getCustomerId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Customer::getId, Function.identity()));
        Map<UUID, CustomerSession> sessionMap = sessions.findAllById(values.stream().map(CustomerBonus::getCustomerSessionId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(CustomerSession::getId, Function.identity()));
        Set<UUID> actorIds = values.stream().flatMap(value -> java.util.stream.Stream.of(value.getCreatedBy(), value.getApprovedBy()))
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, User> actorMap = users.findAllById(actorIds).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return values.stream().map(value -> response(value, customerMap.get(value.getCustomerId()),
                sessionMap.get(value.getCustomerSessionId()), actorMap)).toList();
    }

    private void validateRole() {
        if (!currentRoles.getCurrentRole().map(permissions::canManageCustomerBonus).orElse(false)) {
            throw new RuntimeException("Access denied. Customer Bonus Management is restricted to Director or Super Admin.");
        }
    }
    private void validateReplay(CustomerBonus value, CreateCustomerBonusRequest request) {
        if (!value.getCustomerId().equals(request.customerId())
                || !value.getCustomerSessionId().equals(request.customerSessionId())
                || value.getBonusType() != request.bonusType()
                || value.getAmount().compareTo(request.amount()) != 0
                || !value.getReason().equals(request.reason().trim())) {
            throw new ResourceConflictException("Idempotency key has already been used for a different Customer Bonus.");
        }
    }
    private CustomerBonusResponse response(CustomerBonus value, Customer customer, CustomerSession session, Map<UUID, User> actorMap) {
        Map<UUID, User> resolvedActors = actorMap == null ? users.findAllById(List.of(value.getCreatedBy(), value.getApprovedBy()))
                .stream().collect(Collectors.toMap(User::getId, Function.identity())) : actorMap;
        User creator = resolvedActors.get(value.getCreatedBy()); User approver = resolvedActors.get(value.getApprovedBy());
        return new CustomerBonusResponse(value.getId(), value.getBonusCode(), value.getCustomerId(),
                customer == null ? null : customer.getCustomerCode(), customer == null ? null : customer.getFullName(),
                value.getCustomerSessionId(), session == null ? null : session.getSessionCode(), value.getBusinessDate(),
                value.getBonusType(), value.getAmount(), value.getReason(), value.getStatus(), actor(creator), actor(approver),
                value.getCreatedAt(), value.getApprovedAt());
    }
    private ActorReferenceResponse actor(User user) { return user == null ? null : new ActorReferenceResponse(user.getId(), user.getUsername()); }
}
