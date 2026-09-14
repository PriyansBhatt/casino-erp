package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.LosingReturn;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.*;

public interface LosingReturnRepository extends JpaRepository<LosingReturn, UUID> {
    @org.springframework.data.jpa.repository.Query("""
            select new com.casino.casinoerp.dto.LosingReturnHistoryResponse(
                l.id, l.losingReturnCode, l.customerId, c.customerCode, c.fullName,
                l.customerSessionId, s.sessionCode, l.businessDate, l.eligibleVerifiedLoss,
                l.returnRate, l.amountPaid, l.paymentMode, l.createdAt, l.createdBy, u.username, l.remarks)
            from LosingReturn l
            left join Customer c on c.id = l.customerId
            left join CustomerSession s on s.id = l.customerSessionId
            left join User u on u.id = l.createdBy
            where l.customerId = :customerId and l.businessDate = :businessDate
            order by l.createdAt desc, l.id desc
            """)
    List<com.casino.casinoerp.dto.LosingReturnHistoryResponse> findHistory(
            @org.springframework.data.repository.query.Param("customerId") UUID customerId,
            @org.springframework.data.repository.query.Param("businessDate") LocalDate businessDate);

    Optional<LosingReturn> findByIdempotencyKey(String key);
    List<LosingReturn> findByCustomerIdAndBusinessDateOrderByCreatedAtAsc(UUID customerId, LocalDate businessDate);
    List<LosingReturn> findByBusinessDateAndCreatedBy(LocalDate businessDate, UUID createdBy);
    List<LosingReturn> findByBusinessDate(LocalDate businessDate);
}
