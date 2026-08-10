package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.CustomerAttachment;
import com.casino.casinoerp.entity.CustomerAttachmentStatus;
import com.casino.casinoerp.entity.CustomerAttachmentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerAttachmentRepository extends JpaRepository<CustomerAttachment, UUID> {

    List<CustomerAttachment> findByCustomer_IdOrderByUploadedAtDesc(UUID customerId);

    Optional<CustomerAttachment> findFirstByCustomer_IdAndAttachmentTypeAndStatusOrderByUploadedAtDesc(
            UUID customerId,
            CustomerAttachmentType attachmentType,
            CustomerAttachmentStatus status
    );
}
