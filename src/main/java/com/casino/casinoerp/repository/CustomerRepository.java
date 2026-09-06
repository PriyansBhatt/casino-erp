package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;
import java.util.List;
import java.util.Optional;
import java.time.LocalDate;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    Optional<Customer> findByCustomerCode(String customerCode);

    boolean existsByCustomerCode(String customerCode);

    @Query(value = """
            select exists (
                select 1
                from customer.customers
                where replace(replace(trim(phone), ' ', ''), '-', '') = :normalizedPhone
            )
            """, nativeQuery = true)
    boolean existsByNormalizedPhone(@Param("normalizedPhone") String normalizedPhone);

    @Query(value = """
            select max(cast(substring(customer_code from 5) as integer))
            from customer.customers
            where customer_code ~ '^CUS-[0-9]+$'
            """, nativeQuery = true)
    Integer findMaximumCustomerCodeNumber();

    List<Customer> findByCustomerCodeContainingIgnoreCaseOrFullNameContainingIgnoreCaseOrPhoneContainingIgnoreCase(
            String customerCode,
            String fullName,
            String phone
    );

    @Query(value = """
            select c.id as "customerId", c.customer_code as "customerCode",
                   c.full_name as "customerName", c.status as "customerStatus",
                   s.id as "customerSessionId", s.session_code as "sessionCode",
                   a.id as "activeAssignmentId"
              from customer.customers c
              join session.customer_sessions s on s.customer_id = c.id
               and upper(s.status) = 'OPEN' and s.business_date = :businessDate
              left join casino.pit_table_customer_assignments a
                on a.customer_session_id = s.id and a.status = 'ACTIVE'
             where (lower(c.customer_code) like lower(concat('%', :query, '%'))
                or lower(c.full_name) like lower(concat('%', :query, '%'))
                or lower(s.session_code) like lower(concat('%', :query, '%')))
             order by c.customer_code
             limit 20
            """, nativeQuery = true)
    List<EligiblePitTablePlayerProjection> searchOpenSessionCandidates(
            @Param("businessDate") LocalDate businessDate, @Param("query") String query);
}
