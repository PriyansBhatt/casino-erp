package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.CustomerIdentityDocument;
import com.casino.casinoerp.entity.IdentityDocumentStatus;
import com.casino.casinoerp.entity.IdentityDocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerIdentityDocumentRepository extends JpaRepository<CustomerIdentityDocument, UUID> {

    List<CustomerIdentityDocument> findByCustomer_IdOrderByCreatedAtDesc(UUID customerId);

    List<CustomerIdentityDocument> findByCustomer_IdAndStatusOrderByCreatedAtDesc(
            UUID customerId,
            IdentityDocumentStatus status
    );

    Optional<CustomerIdentityDocument> findFirstByCustomer_IdAndPrimaryDocumentTrueAndStatus(
            UUID customerId,
            IdentityDocumentStatus status
    );

    @Query("""
            select (count(document) > 0)
            from CustomerIdentityDocument document
            where document.documentType = :documentType
              and upper(coalesce(document.issuingCountry, '')) = upper(coalesce(:issuingCountry, ''))
              and document.normalizedDocumentNumber = :normalizedDocumentNumber
              and document.status = :status
            """)
    boolean existsActiveDuplicate(
            @Param("documentType") IdentityDocumentType documentType,
            @Param("issuingCountry") String issuingCountry,
            @Param("normalizedDocumentNumber") String normalizedDocumentNumber,
            @Param("status") IdentityDocumentStatus status
    );
}
