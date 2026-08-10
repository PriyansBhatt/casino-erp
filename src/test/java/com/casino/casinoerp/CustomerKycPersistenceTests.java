package com.casino.casinoerp;

import com.casino.casinoerp.entity.Customer;
import com.casino.casinoerp.entity.CustomerAttachment;
import com.casino.casinoerp.entity.CustomerAttachmentType;
import com.casino.casinoerp.entity.CustomerCategory;
import com.casino.casinoerp.entity.CustomerIdentityDocument;
import com.casino.casinoerp.entity.CustomerStatus;
import com.casino.casinoerp.entity.IdentityDocumentStatus;
import com.casino.casinoerp.entity.IdentityDocumentType;
import com.casino.casinoerp.entity.KycStatus;
import com.casino.casinoerp.entity.User;
import com.casino.casinoerp.repository.CustomerAttachmentRepository;
import com.casino.casinoerp.repository.CustomerIdentityDocumentRepository;
import com.casino.casinoerp.repository.CustomerRepository;
import com.casino.casinoerp.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class CustomerKycPersistenceTests {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomerAttachmentRepository attachmentRepository;

    @Autowired
    private CustomerIdentityDocumentRepository documentRepository;

    private Customer customer;
    private User user;

    @BeforeEach
    void loadExistingForeignKeyRecords() {
        user = userRepository.findAll().stream().findFirst().orElseThrow();

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setCustomerCode("KYC-" + suffix);
        customer.setFullName("KYC Persistence Test");
        customer.setPhone("+97798" + String.format("%08d", Math.floorMod(suffix.hashCode(), 100_000_000)));
        customer.setNationality("Nepali");
        customer.setStatus(CustomerStatus.ACTIVE);
        customer.setCategory(CustomerCategory.NORMAL);
        customer.setKycStatus(KycStatus.PENDING);
        customer = customerRepository.saveAndFlush(customer);
    }

    @Test
    void repositoriesPersistAndQueryCustomerKycMetadata() {
        CustomerAttachment attachment = new CustomerAttachment();
        attachment.setCustomer(customer);
        attachment.setAttachmentType(CustomerAttachmentType.IDENTITY_DOCUMENT);
        attachment.setStorageKey("test/kyc/" + UUID.randomUUID());
        attachment.setOriginalFilename("identity-document.pdf");
        attachment.setContentType("application/pdf");
        attachment.setSizeBytes(1024);
        attachment.setChecksum(UUID.randomUUID().toString().replace("-", ""));
        attachment.setUploadedBy(user);
        attachment = attachmentRepository.saveAndFlush(attachment);

        String firstNumber = "TEST" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        CustomerIdentityDocument primary = document(
                firstNumber,
                "NP",
                true,
                attachment
        );
        documentRepository.saveAndFlush(primary);

        String secondNumber = "TEST" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        documentRepository.saveAndFlush(document(secondNumber, null, false, null));

        List<CustomerIdentityDocument> customerDocuments =
                documentRepository.findByCustomer_IdOrderByCreatedAtDesc(customer.getId());
        List<CustomerIdentityDocument> activeDocuments =
                documentRepository.findByCustomer_IdAndStatusOrderByCreatedAtDesc(
                        customer.getId(),
                        IdentityDocumentStatus.ACTIVE
                );

        assertThat(attachmentRepository.findByCustomer_IdOrderByUploadedAtDesc(customer.getId()))
                .extracting(CustomerAttachment::getId)
                .contains(attachment.getId());
        assertThat(customerDocuments).hasSizeGreaterThanOrEqualTo(2);
        assertThat(activeDocuments).hasSizeGreaterThanOrEqualTo(2);
        assertThat(documentRepository.findFirstByCustomer_IdAndPrimaryDocumentTrueAndStatus(
                customer.getId(),
                IdentityDocumentStatus.ACTIVE
        )).get().extracting(CustomerIdentityDocument::getId).isEqualTo(primary.getId());
        assertThat(documentRepository.existsActiveDuplicate(
                IdentityDocumentType.PASSPORT,
                "np",
                firstNumber,
                IdentityDocumentStatus.ACTIVE
        )).isTrue();
        assertThat(documentRepository.existsActiveDuplicate(
                IdentityDocumentType.PASSPORT,
                "NP",
                "UNKNOWN-" + firstNumber,
                IdentityDocumentStatus.ACTIVE
        )).isFalse();
    }

    private CustomerIdentityDocument document(
            String normalizedNumber,
            String issuingCountry,
            boolean primary,
            CustomerAttachment attachment
    ) {
        CustomerIdentityDocument document = new CustomerIdentityDocument();
        document.setCustomer(customer);
        document.setDocumentType(IdentityDocumentType.PASSPORT);
        document.setDocumentNumber(normalizedNumber);
        document.setNormalizedDocumentNumber(normalizedNumber);
        document.setIssuingCountry(issuingCountry);
        document.setIssuedDate(LocalDate.of(2025, 1, 1));
        document.setExpiryDate(LocalDate.of(2035, 1, 1));
        document.setAttachment(attachment);
        document.setPrimaryDocument(primary);
        document.setCreatedBy(user);
        return document;
    }
}
