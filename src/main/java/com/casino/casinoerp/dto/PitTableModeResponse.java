package com.casino.casinoerp.dto;

import com.casino.casinoerp.entity.PitTableCustomerAssignmentStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PitTableModeResponse(
        UUID operationId,
        UUID physicalTableId,
        String tableCode,
        String tableName,
        String gameType,
        LocalDate businessDate,
        String status,
        BigDecimal openingFloat,
        Integer maxPlayers,
        Staff activeDealer,
        Staff activeSupervisor,
        List<Player> players,
        Custody tableCustody,
        boolean systemLocked,
        boolean currentBusinessDateOpen,
        LocalDate currentBusinessDate,
        List<Integer> supportedDenominations,
        BigDecimal operationVerifiedWins, BigDecimal operationVerifiedLosses) {

    public record Staff(UUID assignmentId, UUID userId, String username,
            String displayName, LocalDateTime startedAt) {
    }

    public record Player(UUID assignmentId, UUID customerId, String customerCode,
            String customerName, UUID customerSessionId, String sessionCode, String badge,
            LocalDateTime joinedAt, PitTableCustomerAssignmentStatus status,
            BigDecimal verifiedWinTotal, BigDecimal verifiedLossTotal, BigDecimal netPosition,
            boolean custodyInitialized, BigDecimal custodyTotal) {
    }

    public record Custody(boolean initialized, Map<Integer, Long> denominations,
            BigDecimal totalValue) {
    }
}
