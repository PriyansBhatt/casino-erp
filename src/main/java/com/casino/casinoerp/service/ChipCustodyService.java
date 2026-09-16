package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.*;
import com.casino.casinoerp.entity.*;
import com.casino.casinoerp.exception.ResourceConflictException;
import com.casino.casinoerp.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ChipCustodyService {
    private static final String CAGE_KEY = "CAGE";

    private final ChipCustodyMovementRepository movements;
    private final ChipCustodyInventoryRepository inventory;
    private final PitTableRepository pitTables;
    private final CustomerSessionRepository customerSessions;
    private final PitTableCustomerAssignmentRepository assignments;
    private final BusinessDateService businessDates;
    private final SystemLockService systemLock;
    private final AuthenticatedUserService authenticatedUsers;
    private final CurrentUserRoleService currentRoles;
    private final RolePermissionService permissions;
    private final AuditLogService audit;
    private final SessionFinancialPositionService financialPositions;
    private final PitTableAccessService tableAccess;
    private final ChipControlReadRepository controlReads;

    public ChipCustodyService(ChipCustodyMovementRepository movements,
            ChipCustodyInventoryRepository inventory, PitTableRepository pitTables,
            CustomerSessionRepository customerSessions,
            PitTableCustomerAssignmentRepository assignments,
            BusinessDateService businessDates,
            SystemLockService systemLock, AuthenticatedUserService authenticatedUsers,
            CurrentUserRoleService currentRoles, RolePermissionService permissions,
            AuditLogService audit, SessionFinancialPositionService financialPositions,
            PitTableAccessService tableAccess, ChipControlReadRepository controlReads) {
        this.movements = movements;
        this.inventory = inventory;
        this.pitTables = pitTables;
        this.customerSessions = customerSessions;
        this.assignments = assignments;
        this.businessDates = businessDates;
        this.systemLock = systemLock;
        this.authenticatedUsers = authenticatedUsers;
        this.currentRoles = currentRoles;
        this.permissions = permissions;
        this.audit = audit;
        this.financialPositions = financialPositions;
        this.tableAccess = tableAccess;
        this.controlReads = controlReads;
    }

    @Transactional
    public ChipCustodyMovementResponse initializeCage(ChipCustodyTransferRequest request) {
        if (!permissions.canInitializeChipCustody(currentRoles.getCurrentRole().orElse(null))) {
            throw new RuntimeException("Access denied. Only Super Admin can initialize cage chip inventory.");
        }
        String key = request.idempotencyKey().trim();
        ChipCustodyMovement replay = movements.findByIdempotencyKey(key).orElse(null);
        if (replay != null) {
            if (request.expectedBusinessDate() != null
                    && !request.expectedBusinessDate().equals(replay.getBusinessDate())) {
                throw new ResourceConflictException("Idempotency key has already been used for a different Business Date.");
            }
            Map<Integer, Long> denominations = normalize(request.denominations());
            validateReplay(replay, ChipCustodyMovementType.CAGE_OPENING, null, null,
                    denominations, total(denominations));
            return response(replay);
        }
        businessDates.validateNewOperationalMutationAllowed();
        businessDates.validateBusinessDateIsOpen();
        if (systemLock.isSystemLocked()) throw new ResourceConflictException("System is locked. Chip custody movements are not allowed.");
        if (request.expectedBusinessDate() != null
                && !request.expectedBusinessDate().equals(businessDates.getCurrentBusinessDate())) {
            throw new ResourceConflictException("Business Date changed. Refresh before initializing cage inventory.");
        }
        ChipDenomination.supportedValues().stream().sorted().forEach(denomination ->
                inventory.findForUpdate(CAGE_KEY, denomination)
                        .orElseThrow(() -> new IllegalStateException("Cage chip inventory is not initialized correctly.")));
        if (movements.count() > 0) {
            throw new ResourceConflictException("Cage opening inventory can only be initialized before the first custody movement.");
        }
        return response(transfer(ChipCustodyMovementType.CAGE_OPENING,
                ChipCustodyLocationType.EXTERNAL, null, ChipCustodyLocationType.CAGE, null,
                request.denominations(), null, null, null, null, key,
                authenticatedUsers.getRequiredUser().getId()));
    }

    @Transactional
    public ChipCustodyMovement recordBuyIn(UUID transactionId, UUID sessionId,
            LocalDate businessDate, Map<Integer, Long> denominations, BigDecimal expectedTotal,
            UUID actorId) {
        businessDates.validateNewOperationalMutationAllowed();
        return transfer(ChipCustodyMovementType.BUY_IN_ISSUE,
                ChipCustodyLocationType.CAGE, null, ChipCustodyLocationType.CUSTOMER_SESSION, sessionId,
                denominations, "CHIP_BUY_IN", transactionId, sessionId, null,
                "BUY_IN_ISSUE:" + transactionId, actorId, expectedTotal, businessDate);
    }

    @Transactional
    public ChipCustodyMovement recordCashOut(UUID transactionId, UUID sessionId,
            LocalDate businessDate, Map<Integer, Long> denominations, BigDecimal expectedTotal,
            UUID actorId) {
        businessDates.validateSettlementMutationAllowed();
        return transfer(ChipCustodyMovementType.CASH_OUT_RETURN,
                ChipCustodyLocationType.CUSTOMER_SESSION, sessionId, ChipCustodyLocationType.CAGE, null,
                denominations, "CHIP_CASH_OUT", transactionId, sessionId, null,
                "CASH_OUT_RETURN:" + transactionId, actorId, expectedTotal, businessDate);
    }

    @Transactional
    public ChipCustodyMovementResponse correctLegacySessionCustody(
            LegacySessionCustodyCorrectionRequest request) {
        if (!permissions.canCorrectLegacyChipCustody(currentRoles.getCurrentRole().orElse(null))) {
            throw new RuntimeException("Access denied. Only Super Admin can correct legacy session custody.");
        }
        businessDates.validateSettlementMutationAllowed();
        businessDates.validateBusinessDateIsOpen();
        if (systemLock.isSystemLocked()) {
            throw new ResourceConflictException(
                    "System is locked. Legacy session custody correction is not allowed.");
        }

        CustomerSession session = customerSessions.findByIdForUpdate(request.customerSessionId())
                .orElseThrow(() -> new com.casino.casinoerp.exception.ResourceNotFoundException(
                        "Customer session not found."));
        if (!"OPEN".equalsIgnoreCase(session.getStatus())) {
            throw new ResourceConflictException(
                    "Customer session must be OPEN for a legacy custody correction.");
        }
        LocalDate currentBusinessDate = businessDates.getCurrentBusinessDate();
        if (!currentBusinessDate.equals(session.getBusinessDate())) {
            throw new ResourceConflictException(
                    "Customer session does not belong to the current OPEN Business Date.");
        }

        String reason = request.reason().trim();
        if (reason.length() < 10) {
            throw new IllegalArgumentException(
                    "Correction reason must be between 10 and 500 characters.");
        }
        Map<Integer, Long> denominations = normalize(request.denominations());
        BigDecimal correctionAmount = total(denominations);
        String idempotencyKey = request.idempotencyKey().trim();
        ChipCustodyMovement replay = movements.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (replay != null) {
            validateReplay(replay, ChipCustodyMovementType.LEGACY_CUSTODY_CORRECTION,
                    null, session.getId(), denominations, correctionAmount);
            if (!Objects.equals(replay.getCorrectionReason(), reason)) {
                throw new ResourceConflictException(
                        "Idempotency key has already been used for a different chip custody movement.");
            }
            return response(replay);
        }
        BigDecimal positionBefore = financialPositions.getPosition(session.getId()).calculatedChipPosition();
        if (positionBefore.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResourceConflictException(
                    "Legacy custody correction requires a positive authoritative financial position.");
        }

        BigDecimal custodyBefore = inventory(
                locationKey(ChipCustodyLocationType.CUSTOMER_SESSION, session.getId()),
                ChipCustodyLocationType.CUSTOMER_SESSION, session.getId()).totalValue();
        if (custodyBefore.compareTo(positionBefore) >= 0) {
            throw new ResourceConflictException(
                    "Customer physical custody already resolves the authoritative financial position.");
        }
        BigDecimal custodyAfter = custodyBefore.add(correctionAmount);
        if (custodyAfter.compareTo(positionBefore) > 0) {
            throw new ResourceConflictException(
                    "Legacy custody correction cannot exceed the authoritative financial position gap.");
        }

        UUID actorId = authenticatedUsers.getRequiredUser().getId();
        ChipCustodyMovement movement = transfer(ChipCustodyMovementType.LEGACY_CUSTODY_CORRECTION,
                ChipCustodyLocationType.CAGE, null,
                ChipCustodyLocationType.CUSTOMER_SESSION, session.getId(), denominations,
                "LEGACY_CUSTODY_CORRECTION", null, session.getId(), null, idempotencyKey,
                actorId, correctionAmount, currentBusinessDate, reason);

        BigDecimal positionAfter = financialPositions.getPosition(session.getId()).calculatedChipPosition();
        if (positionAfter.compareTo(positionBefore) != 0) {
            throw new IllegalStateException(
                    "Legacy custody correction unexpectedly changed the authoritative financial position.");
        }
        audit.log("LEGACY_SESSION_CUSTODY_CORRECTION", "CHIP_CUSTODY_MOVEMENT",
                movement.getId(), actorId,
                "sessionId=" + session.getId() + ", businessDate=" + currentBusinessDate
                        + ", correctionAmount=" + correctionAmount + ", denominations=" + denominations
                        + ", resultingCustodyTotal=" + custodyAfter + ", reason=" + reason);
        return response(movement);
    }

    @Transactional
    public ChipCustodyMovementResponse issueTableFloat(UUID tableId, ChipCustodyTransferRequest request) {
        validatePitCustodyRole();
        ChipCustodyMovement completed = completedTransfer(request, ChipCustodyMovementType.TABLE_FLOAT_ISSUE, null, tableId);
        if (completed != null) return response(completed);
        businessDates.validateNewOperationalMutationAllowed();
        LocalDate date = currentWritableBusinessDate();
        PitTable table = validateOpenTableForUpdate(tableId, date);
        String key = request.idempotencyKey().trim();
        if (movements.findByIdempotencyKey(key).isEmpty()
                && movements.existsByMovementTypeAndPitTableId(
                ChipCustodyMovementType.TABLE_FLOAT_ISSUE, tableId)) {
            throw new ResourceConflictException("Opening chip float has already been issued to this Pit Table.");
        }
        return response(transfer(ChipCustodyMovementType.TABLE_FLOAT_ISSUE,
                ChipCustodyLocationType.CAGE, null, ChipCustodyLocationType.PIT_TABLE, tableId,
                request.denominations(), "PIT_TABLE", tableId, null, tableId,
                key, authenticatedUsers.getRequiredUser().getId(),
                table.getOpeningFloat() == null ? BigDecimal.ZERO : table.getOpeningFloat(), date));
    }

    public BigDecimal calculateDenominationTotal(Map<Integer, Long> denominations) {
        return total(normalize(denominations));
    }

    public void validateTableFloatIssueReplay(
            UUID tableId, ChipCustodyTransferRequest request) {
        Map<Integer, Long> denominations = normalize(request.denominations());
        BigDecimal requestedTotal = total(denominations);
        ChipCustodyMovement replay = movements.findByIdempotencyKey(request.idempotencyKey().trim())
                .orElseThrow(() -> new ResourceConflictException(
                        "Table opening idempotency record is incomplete."));
        validateReplay(replay, ChipCustodyMovementType.TABLE_FLOAT_ISSUE,
                null, tableId, denominations, requestedTotal);
    }

    @Transactional
    public ChipCustodyMovementResponse returnTableFloat(UUID tableId, ChipCustodyTransferRequest request) {
        validatePitCustodyRole();
        ChipCustodyMovement completed = completedTransfer(request, ChipCustodyMovementType.TABLE_FLOAT_RETURN, tableId, null);
        if (completed != null) return response(completed);
        businessDates.validateSettlementMutationAllowed();
        LocalDate date = currentWritableBusinessDate();
        validateOpenTableForUpdate(tableId, date);
        return response(transfer(ChipCustodyMovementType.TABLE_FLOAT_RETURN,
                ChipCustodyLocationType.PIT_TABLE, tableId, ChipCustodyLocationType.CAGE, null,
                request.denominations(), "PIT_TABLE", tableId, null, tableId,
                request.idempotencyKey().trim(), authenticatedUsers.getRequiredUser().getId(), null, date));
    }

    @Transactional
    public ChipCustodyMovementResponse moveCustomerChipsToTable(
            UUID tableId, UUID sessionId, ChipCustodyTransferRequest request) {
        validatePitTransactionRole();
        ChipCustodyMovement completed = completedTransfer(request, ChipCustodyMovementType.CUSTOMER_TO_TABLE, sessionId, tableId);
        if (completed != null) return response(completed);
        businessDates.validateNewOperationalMutationAllowed();
        TransferContext context = validateCustomerTableTransferForUpdate(tableId, sessionId);
        return response(transfer(ChipCustodyMovementType.CUSTOMER_TO_TABLE,
                ChipCustodyLocationType.CUSTOMER_SESSION, sessionId,
                ChipCustodyLocationType.PIT_TABLE, tableId,
                request.denominations(), "PIT_TABLE_ASSIGNMENT", context.assignment().getId(),
                sessionId, tableId, request.idempotencyKey().trim(),
                authenticatedUsers.getRequiredUser().getId(), null, context.businessDate()));
    }

    @Transactional
    public ChipCustodyMovementResponse returnTableChipsToCustomer(
            UUID tableId, UUID sessionId, ChipCustodyTransferRequest request) {
        validatePitTransactionRole();
        ChipCustodyMovement completed = completedTransfer(request, ChipCustodyMovementType.TABLE_TO_CUSTOMER, tableId, sessionId);
        if (completed != null) return response(completed);
        businessDates.validateSettlementMutationAllowed();
        TransferContext context = validateCustomerTableTransferForUpdate(tableId, sessionId);
        return response(recordTableToCustomer(context.assignment(), request.denominations(),
                request.idempotencyKey().trim(), authenticatedUsers.getRequiredUser().getId(),
                context.businessDate()));
    }

    @Transactional
    public void settleAssignmentForLeave(PitTableCustomerAssignment assignment,
            Map<Integer, Long> returnedDenominations, String idempotencyKey, UUID actorId) {
        businessDates.validateSettlementMutationAllowed();
        TransferContext context = validateCustomerTableTransferForUpdate(
                assignment.getPitTableId(), assignment.getCustomerSessionId());
        if (!context.assignment().getId().equals(assignment.getId())) {
            throw new ResourceConflictException("The active Pit Table assignment changed during settlement.");
        }
        Map<Integer, Long> normalized = normalizeOptional(returnedDenominations);
        if (!normalized.isEmpty()) {
            recordTableToCustomer(assignment, normalized, idempotencyKey, actorId,
                    context.businessDate());
        }
    }

    @Transactional(readOnly = true)
    public void validateAssignmentLeaveReplay(PitTableCustomerAssignment assignment,
            Map<Integer, Long> returnedDenominations, String idempotencyKey) {
        Map<Integer, Long> normalized = normalizeOptional(returnedDenominations);
        ChipCustodyMovement movement = movements.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (normalized.isEmpty()) {
            if (movement != null) {
                throw new ResourceConflictException(
                        "Idempotency key was already used with a different request.");
            }
            return;
        }
        if (movement == null
                || !"PIT_TABLE_ASSIGNMENT".equals(movement.getRelatedTransactionType())
                || !assignment.getId().equals(movement.getRelatedTransactionId())
                || !assignment.getCustomerSessionId().equals(movement.getCustomerSessionId())
                || !assignment.getPitTableId().equals(movement.getPitTableId())) {
            throw new ResourceConflictException(
                    "Idempotency key was already used with a different request.");
        }
        try {
            validateReplay(movement, ChipCustodyMovementType.TABLE_TO_CUSTOMER,
                    assignment.getPitTableId(), assignment.getCustomerSessionId(),
                    normalized, total(normalized));
        } catch (ResourceConflictException exception) {
            throw new ResourceConflictException(
                    "Idempotency key was already used with a different request.");
        }
    }

    @Transactional(readOnly = true)
    public ChipCustodyInventoryResponse cageInventory() {
        validateCustodyReadRole();
        if (tableAccess.isDealer()) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Dealers cannot view cage chip custody.");
        }
        return inventory(CAGE_KEY, ChipCustodyLocationType.CAGE, null);
    }

    @Transactional(readOnly = true)
    public ChipCustodyInventoryResponse customerSessionInventory(UUID sessionId) {
        validateCustodyReadRole();
        tableAccess.requireCustomerSessionAccess(sessionId);
        return inventory(locationKey(ChipCustodyLocationType.CUSTOMER_SESSION, sessionId),
                ChipCustodyLocationType.CUSTOMER_SESSION, sessionId);
    }

    @Transactional(readOnly = true)
    public ChipCustodyInventoryResponse tableInventory(UUID tableId) {
        validateCustodyReadRole();
        if (tableAccess.isDealer()) {
            tableAccess.requireOperationalAccess(tableId);
        }
        return inventory(locationKey(ChipCustodyLocationType.PIT_TABLE, tableId),
                ChipCustodyLocationType.PIT_TABLE, tableId);
    }

    @Transactional(readOnly = true)
    public void validateTableCustodySettled(UUID tableId) {
        ChipCustodyInventoryResponse position = inventory(
                locationKey(ChipCustodyLocationType.PIT_TABLE, tableId),
                ChipCustodyLocationType.PIT_TABLE, tableId);
        if (position.denominations().values().stream().anyMatch(quantity -> quantity != 0)) {
            throw new ResourceConflictException(
                    "All physical Pit Table chips must be returned to the cage before the table can be closed.");
        }
    }

    @Transactional(readOnly = true)
    public List<ChipCustodyMovementResponse> currentHistory() {
        if (!permissions.canViewChipCustodyHistory(currentRoles.getCurrentRole().orElse(null))) {
            throw new RuntimeException("Access denied. Chip custody history is restricted.");
        }
        businessDates.validateBusinessDateIsOpen();
        var rows = movements.findByBusinessDateOrderByCreatedAtDescIdDesc(businessDates.getCurrentBusinessDate());
        var displays = controlReads.movementDisplays(rows.stream().map(ChipCustodyMovement::getId).toList());
        return rows.stream().map(row -> response(row, displays.get(row.getId()))).toList();
    }

    private ChipCustodyMovement transfer(ChipCustodyMovementType movementType,
            ChipCustodyLocationType sourceType, UUID sourceReferenceId,
            ChipCustodyLocationType destinationType, UUID destinationReferenceId,
            Map<Integer, Long> requested, String relatedType, UUID relatedId,
            UUID sessionId, UUID tableId, String idempotencyKey, UUID actorId) {
        return transfer(movementType, sourceType, sourceReferenceId, destinationType, destinationReferenceId,
                requested, relatedType, relatedId, sessionId, tableId, idempotencyKey, actorId, null,
                businessDates.getCurrentBusinessDate());
    }

    private ChipCustodyMovement transfer(ChipCustodyMovementType movementType,
            ChipCustodyLocationType sourceType, UUID sourceReferenceId,
            ChipCustodyLocationType destinationType, UUID destinationReferenceId,
            Map<Integer, Long> requested, String relatedType, UUID relatedId,
            UUID sessionId, UUID tableId, String idempotencyKey, UUID actorId,
            BigDecimal expectedTotal, LocalDate businessDate) {
        return transfer(movementType, sourceType, sourceReferenceId, destinationType,
                destinationReferenceId, requested, relatedType, relatedId, sessionId, tableId,
                idempotencyKey, actorId, expectedTotal, businessDate, null);
    }

    private ChipCustodyMovement transfer(ChipCustodyMovementType movementType,
            ChipCustodyLocationType sourceType, UUID sourceReferenceId,
            ChipCustodyLocationType destinationType, UUID destinationReferenceId,
            Map<Integer, Long> requested, String relatedType, UUID relatedId,
            UUID sessionId, UUID tableId, String idempotencyKey, UUID actorId,
            BigDecimal expectedTotal, LocalDate businessDate, String correctionReason) {
        ChipCustodyMovement replay = movements.findByIdempotencyKey(idempotencyKey).orElse(null);
        Map<Integer, Long> denominations = normalize(requested);
        BigDecimal total = total(denominations);
        if (expectedTotal != null && expectedTotal.compareTo(total) != 0) {
            throw new IllegalArgumentException("Chip denomination total does not match the financial transaction amount.");
        }
        if (replay != null) {
            validateReplay(replay, movementType, sourceReferenceId, destinationReferenceId, denominations, total);
            return replay;
        }

        String sourceKey = locationKey(sourceType, sourceReferenceId);
        String destinationKey = locationKey(destinationType, destinationReferenceId);
        List<Integer> ordered = denominations.keySet().stream().sorted().toList();
        Map<Integer, ChipCustodyInventory> sourceRows = new LinkedHashMap<>();
        Map<Integer, ChipCustodyInventory> destinationRows = new LinkedHashMap<>();
        for (Integer denomination : ordered) {
            if (sourceType != ChipCustodyLocationType.EXTERNAL) {
                ChipCustodyInventory source = inventory.findForUpdate(sourceKey, denomination)
                        .orElseThrow(() -> new ResourceConflictException(
                                "Insufficient physical chips for denomination NPR " + denomination + "."));
                if (source.getQuantity() < denominations.get(denomination)) {
                    throw new ResourceConflictException(
                            "Insufficient physical chips for denomination NPR " + denomination + ".");
                }
                sourceRows.put(denomination, source);
            }
            if (destinationType != ChipCustodyLocationType.EXTERNAL) {
                destinationRows.put(denomination, inventory.findForUpdate(destinationKey, denomination)
                        .orElseGet(() -> newInventory(destinationKey, destinationType,
                                destinationReferenceId, denomination)));
            }
        }
        replay = movements.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (replay != null) {
            validateReplay(replay, movementType, sourceReferenceId, destinationReferenceId, denominations, total);
            return replay;
        }
        denominations.forEach((denomination, quantity) -> {
            ChipCustodyInventory source = sourceRows.get(denomination);
            if (source != null) source.setQuantity(Math.subtractExact(source.getQuantity(), quantity));
            ChipCustodyInventory destination = destinationRows.get(denomination);
            if (destination != null) destination.setQuantity(Math.addExact(destination.getQuantity(), quantity));
        });
        inventory.saveAll(sourceRows.values());
        inventory.saveAll(destinationRows.values());

        ChipCustodyMovement movement = new ChipCustodyMovement();
        movement.setMovementType(movementType);
        movement.setBusinessDate(businessDate);
        movement.setSourceType(sourceType);
        movement.setSourceReferenceId(sourceReferenceId);
        movement.setDestinationType(destinationType);
        movement.setDestinationReferenceId(destinationReferenceId);
        movement.setRelatedTransactionType(relatedType);
        movement.setRelatedTransactionId(relatedId);
        movement.setCustomerSessionId(sessionId);
        movement.setPitTableId(tableId);
        movement.setDenominations(new LinkedHashMap<>(denominations));
        movement.setTotalValue(total);
        movement.setIdempotencyKey(idempotencyKey);
        movement.setCreatedBy(actorId);
        movement.setCreatedAt(LocalDateTime.now());
        movement.setCorrectionReason(correctionReason);
        ChipCustodyMovement saved = movements.save(movement);
        audit.log("CREATE_CHIP_CUSTODY_MOVEMENT", "CHIP_CUSTODY_MOVEMENT", saved.getId(), actorId,
                movementType + ", totalValue=" + total + ", businessDate=" + businessDate);
        return saved;
    }

    private Map<Integer, Long> normalize(Map<Integer, Long> requested) {
        if (requested == null || requested.isEmpty()) {
            throw new IllegalArgumentException("At least one chip denomination quantity is required.");
        }
        Map<Integer, Long> normalized = new LinkedHashMap<>();
        requested.forEach((denomination, quantity) -> {
            if (!ChipDenomination.supports(denomination)) {
                throw new IllegalArgumentException("Unsupported chip denomination: " + denomination);
            }
            if (quantity == null || quantity < 0) {
                throw new IllegalArgumentException("Chip denomination quantities must be non-negative integers.");
            }
            if (quantity > 0) normalized.put(denomination, quantity);
        });
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("At least one chip denomination quantity must be greater than zero.");
        }
        total(normalized);
        return normalized;
    }

    private BigDecimal total(Map<Integer, Long> denominations) {
        try {
            long total = 0;
            for (Map.Entry<Integer, Long> entry : denominations.entrySet()) {
                total = Math.addExact(total, Math.multiplyExact(entry.getKey().longValue(), entry.getValue()));
            }
            return BigDecimal.valueOf(total);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Chip denomination total exceeds the supported monetary range.");
        }
    }

    private ChipCustodyInventory newInventory(String key, ChipCustodyLocationType type,
            UUID referenceId, Integer denomination) {
        ChipCustodyInventory value = new ChipCustodyInventory();
        value.setLocationKey(key);
        value.setLocationType(type);
        value.setReferenceId(referenceId);
        value.setDenomination(denomination);
        value.setQuantity(0L);
        return value;
    }

    private String locationKey(ChipCustodyLocationType type, UUID referenceId) {
        if (type == ChipCustodyLocationType.CAGE) return CAGE_KEY;
        if (type == ChipCustodyLocationType.EXTERNAL) return "EXTERNAL";
        if (referenceId == null) throw new IllegalArgumentException(type + " custody requires a reference ID.");
        return type.name() + ":" + referenceId;
    }

    private ChipCustodyInventoryResponse inventory(String key, ChipCustodyLocationType type, UUID referenceId) {
        Map<Integer, Long> values = new LinkedHashMap<>();
        ChipDenomination.supportedValues().stream().sorted().forEach(value -> values.put(value, 0L));
        inventory.findByLocationKeyOrderByDenomination(key)
                .forEach(row -> values.put(row.getDenomination(), row.getQuantity()));
        boolean initialized = type != ChipCustodyLocationType.CAGE
                || movements.existsByMovementType(ChipCustodyMovementType.CAGE_OPENING);
        return new ChipCustodyInventoryResponse(type, referenceId, initialized,
                Map.copyOf(values), total(values));
    }

    private ChipCustodyMovement completedTransfer(ChipCustodyTransferRequest request,
            ChipCustodyMovementType type, UUID source, UUID destination) {
        ChipCustodyMovement value = movements.findByIdempotencyKey(request.idempotencyKey().trim()).orElse(null);
        if (value != null) {
            Map<Integer, Long> quantities = normalize(request.denominations());
            validateReplay(value, type, source, destination, quantities, total(quantities));
        }
        return value;
    }

    private void validateReplay(ChipCustodyMovement replay, ChipCustodyMovementType type,
            UUID sourceReferenceId, UUID destinationReferenceId, Map<Integer, Long> denominations,
            BigDecimal total) {
        if (!Objects.equals(replay.getCreatedBy(), authenticatedUsers.getRequiredUser().getId())
                || replay.getMovementType() != type
                || !Objects.equals(replay.getSourceReferenceId(), sourceReferenceId)
                || !Objects.equals(replay.getDestinationReferenceId(), destinationReferenceId)
                || !denominations.equals(replay.getDenominations())
                || total.compareTo(replay.getTotalValue()) != 0) {
            throw new ResourceConflictException("Idempotency key has already been used for a different chip custody movement.");
        }
    }

    private LocalDate currentWritableBusinessDate() {
        businessDates.validateBusinessDateIsOpen();
        if (systemLock.isSystemLocked()) throw new ResourceConflictException("System is locked. Chip custody movements are not allowed.");
        return businessDates.getCurrentBusinessDate();
    }

    private void validatePitCustodyRole() {
        if (!permissions.canManageTableChipCustody(currentRoles.getCurrentRole().orElse(null))) {
            throw new RuntimeException("Access denied. Table chip custody is restricted.");
        }
    }

    private void validatePitTransactionRole() {
        if (!permissions.canPitTransaction(currentRoles.getCurrentUserRole())) {
            throw new RuntimeException(
                    "Access denied. Only Dealer, Pit Supervisor or Super Admin can transfer customer table chips.");
        }
    }

    private TransferContext validateCustomerTableTransferForUpdate(UUID tableId, UUID sessionId) {
        LocalDate date = currentWritableBusinessDate();
        CustomerSession session = customerSessions.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new com.casino.casinoerp.exception.ResourceNotFoundException(
                        "Customer session not found."));
        if (!"OPEN".equalsIgnoreCase(session.getStatus()) || session.getExitTime() != null) {
            throw new ResourceConflictException("Customer session must be OPEN and unexited for a chip custody transfer.");
        }
        if (!date.equals(session.getBusinessDate())) {
            throw new ResourceConflictException(
                    "Customer session does not belong to the current OPEN Business Date.");
        }
        PitTable table = validateOpenTableForUpdate(tableId, date);
        tableAccess.requireOperationalAccess(tableId);
        PitTableCustomerAssignment assignment = assignments.findActiveForUpdate(sessionId, tableId)
                .orElseThrow(() -> new ResourceConflictException(
                        "Customer session must be actively assigned to this Pit Table."));
        if (!date.equals(assignment.getBusinessDate())) {
            throw new ResourceConflictException(
                    "Pit Table assignment does not belong to the current OPEN Business Date.");
        }
        return new TransferContext(session, table, assignment, date);
    }

    private ChipCustodyMovement recordTableToCustomer(PitTableCustomerAssignment assignment,
            Map<Integer, Long> denominations, String idempotencyKey, UUID actorId,
            LocalDate businessDate) {
        return transfer(ChipCustodyMovementType.TABLE_TO_CUSTOMER,
                ChipCustodyLocationType.PIT_TABLE, assignment.getPitTableId(),
                ChipCustodyLocationType.CUSTOMER_SESSION, assignment.getCustomerSessionId(),
                denominations, "PIT_TABLE_ASSIGNMENT", assignment.getId(),
                assignment.getCustomerSessionId(), assignment.getPitTableId(), idempotencyKey,
                actorId, null, businessDate);
    }

    private Map<Integer, Long> normalizeOptional(Map<Integer, Long> requested) {
        if (requested == null || requested.isEmpty()) return Map.of();
        if (requested.values().stream().allMatch(value -> value != null && value == 0)) {
            requested.keySet().forEach(denomination -> {
                if (!ChipDenomination.supports(denomination)) {
                    throw new IllegalArgumentException("Unsupported chip denomination: " + denomination);
                }
            });
            return Map.of();
        }
        return normalize(requested);
    }

    private record TransferContext(CustomerSession session, PitTable table,
                                   PitTableCustomerAssignment assignment, LocalDate businessDate) {}

    private void validateCustodyReadRole() {
        if (!permissions.canViewChipCustody(currentRoles.getCurrentRole().orElse(null))) {
            throw new RuntimeException("Access denied. Chip custody inventory is restricted.");
        }
    }

    private PitTable validateOpenTableForUpdate(UUID tableId, LocalDate businessDate) {
        PitTable table = pitTables.findByIdForUpdate(tableId)
                .orElseThrow(() -> new com.casino.casinoerp.exception.ResourceNotFoundException("Pit table not found."));
        if (!"OPEN".equalsIgnoreCase(table.getStatus())) {
            throw new ResourceConflictException("Pit table must be OPEN for a chip float movement.");
        }
        if (!businessDate.equals(table.getBusinessDate())) {
            throw new ResourceConflictException("Pit table does not belong to the current OPEN Business Date.");
        }
        return table;
    }

    private ChipCustodyMovementResponse response(ChipCustodyMovement movement) {
        return response(movement, null);
    }

    private ChipCustodyMovementResponse response(ChipCustodyMovement movement, ChipCustodyDisplayResponse display) {
        return new ChipCustodyMovementResponse(movement.getId(), movement.getMovementType(),
                movement.getBusinessDate(), movement.getSourceType(), movement.getSourceReferenceId(),
                movement.getDestinationType(), movement.getDestinationReferenceId(),
                movement.getRelatedTransactionType(), movement.getRelatedTransactionId(),
                movement.getCustomerSessionId(), movement.getPitTableId(),
                Map.copyOf(movement.getDenominations()), movement.getTotalValue(),
                movement.getCreatedBy(), movement.getCreatedAt(), movement.getCorrectionReason(), display);
    }
}
