package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.ActorReferenceResponse;
import com.casino.casinoerp.dto.CustomerAttachmentMetadataResponse;
import com.casino.casinoerp.dto.CustomerKycUpdateRequest;
import com.casino.casinoerp.dto.IdentityDocumentRequest;
import com.casino.casinoerp.dto.IdentityDocumentResponse;
import com.casino.casinoerp.dto.PrivilegedCustomerClassificationRequest;
import com.casino.casinoerp.dto.PrivilegedCustomerKycResponse;
import com.casino.casinoerp.dto.PrivilegedIdentityDocumentResponse;
import com.casino.casinoerp.dto.ReceptionCustomerKycResponse;
import com.casino.casinoerp.entity.Customer;
import com.casino.casinoerp.entity.CustomerAttachment;
import com.casino.casinoerp.entity.CustomerIdentityDocument;
import com.casino.casinoerp.entity.IdentityDocumentStatus;
import com.casino.casinoerp.entity.User;
import com.casino.casinoerp.exception.ResourceConflictException;
import com.casino.casinoerp.exception.ResourceNotFoundException;
import com.casino.casinoerp.repository.CustomerAttachmentRepository;
import com.casino.casinoerp.repository.CustomerIdentityDocumentRepository;
import com.casino.casinoerp.repository.CustomerRepository;
import com.casino.casinoerp.repository.CustomerSessionRepository;
import com.casino.casinoerp.repository.CustomerVisitSummaryProjection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class CustomerKycService {

    private static final int MAX_GENDER_LENGTH = 30;
    private static final int MAX_ADDRESS_LENGTH = 1000;
    private static final int MAX_EMAIL_LENGTH = 254;
    private static final int MAX_OCCUPATION_LENGTH = 150;
    private static final int MAX_ISSUING_COUNTRY_LENGTH = 100;
    private static final int MAX_DOCUMENT_NUMBER_LENGTH = 100;
    private static final int MAX_INTERNAL_NOTES_LENGTH = 4000;
    private static final Pattern BASIC_EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final CustomerRepository customerRepository;
    private final CustomerIdentityDocumentRepository documentRepository;
    private final CustomerAttachmentRepository attachmentRepository;
    private final CustomerSessionRepository sessionRepository;
    private final IdentityDocumentNumberNormalizer documentNumberNormalizer;
    private final AuthenticatedUserService authenticatedUserService;

    public CustomerKycService(
            CustomerRepository customerRepository,
            CustomerIdentityDocumentRepository documentRepository,
            CustomerAttachmentRepository attachmentRepository,
            CustomerSessionRepository sessionRepository,
            IdentityDocumentNumberNormalizer documentNumberNormalizer,
            AuthenticatedUserService authenticatedUserService
    ) {
        this.customerRepository = customerRepository;
        this.documentRepository = documentRepository;
        this.attachmentRepository = attachmentRepository;
        this.sessionRepository = sessionRepository;
        this.documentNumberNormalizer = documentNumberNormalizer;
        this.authenticatedUserService = authenticatedUserService;
    }

    @Transactional
    public ReceptionCustomerKycResponse updateBasicKyc(UUID customerId, CustomerKycUpdateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("KYC update is required.");
        }
        validateDateOfBirth(request.dateOfBirth());

        String gender = normalizeOptionalText(request.gender(), MAX_GENDER_LENGTH, "Gender");
        String permanentAddress = normalizeOptionalText(
                request.permanentAddress(), MAX_ADDRESS_LENGTH, "Permanent address");
        String currentAddress = normalizeOptionalText(
                request.currentAddress(), MAX_ADDRESS_LENGTH, "Current address");
        String email = normalizeOptionalText(request.email(), MAX_EMAIL_LENGTH, "Email");
        String occupation = normalizeOptionalText(request.occupation(), MAX_OCCUPATION_LENGTH, "Occupation");

        if (email != null && !BASIC_EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Invalid email address.");
        }

        Customer customer = getRequiredCustomer(customerId);
        customer.setDateOfBirth(request.dateOfBirth());
        customer.setGender(gender);
        customer.setPermanentAddress(permanentAddress);
        customer.setCurrentAddress(currentAddress);
        customer.setEmail(email);
        customer.setOccupation(occupation);

        return toReceptionResponse(customerRepository.save(customer));
    }

    @Transactional
    public IdentityDocumentResponse createIdentityDocument(UUID customerId, IdentityDocumentRequest request) {
        validateIdentityRequest(request);

        String normalizedNumber = documentNumberNormalizer.normalize(request.documentNumber());
        if (normalizedNumber.length() > MAX_DOCUMENT_NUMBER_LENGTH) {
            throw new IllegalArgumentException(
                    "Document number must not exceed " + MAX_DOCUMENT_NUMBER_LENGTH + " characters.");
        }
        String issuingCountry = normalizeOptionalText(
                request.issuingCountry(), MAX_ISSUING_COUNTRY_LENGTH, "Issuing country");
        Customer customer = getRequiredCustomer(customerId);

        if (documentRepository.existsActiveDuplicate(
                request.documentType(),
                issuingCountry,
                normalizedNumber,
                IdentityDocumentStatus.ACTIVE
        )) {
            throw new ResourceConflictException(
                    "An active identity document with these details already exists.");
        }

        User actor = authenticatedUserService.getRequiredUser();
        boolean primaryDocument = Boolean.TRUE.equals(request.primaryDocument());

        CustomerIdentityDocument existingPrimary = null;
        if (primaryDocument) {
            existingPrimary = documentRepository
                    .findFirstByCustomer_IdAndPrimaryDocumentTrueAndStatus(
                            customerId,
                            IdentityDocumentStatus.ACTIVE
                    )
                    .orElse(null);
        }

        CustomerIdentityDocument document = new CustomerIdentityDocument();
        document.setCustomer(customer);
        document.setDocumentType(request.documentType());
        document.setDocumentNumber(normalizeRequiredDisplayText(
                request.documentNumber(), MAX_DOCUMENT_NUMBER_LENGTH, "Document number"));
        document.setNormalizedDocumentNumber(normalizedNumber);
        document.setIssuingCountry(issuingCountry);
        document.setIssuedDate(request.issuedDate());
        document.setExpiryDate(request.expiryDate());
        document.setPrimaryDocument(primaryDocument);
        document.setStatus(IdentityDocumentStatus.ACTIVE);
        document.setCreatedBy(actor);

        if (existingPrimary != null) {
            existingPrimary.setPrimaryDocument(false);
            existingPrimary.setStatus(IdentityDocumentStatus.REPLACED);
            documentRepository.saveAndFlush(existingPrimary);
        }

        return toIdentityResponse(documentRepository.save(document));
    }

    @Transactional(readOnly = true)
    public ReceptionCustomerKycResponse getReceptionKyc(UUID customerId) {
        return toReceptionResponse(getRequiredCustomer(customerId));
    }

    @Transactional(readOnly = true)
    public PrivilegedCustomerKycResponse getPrivilegedKyc(UUID customerId) {
        return toPrivilegedResponse(getRequiredCustomer(customerId));
    }

    @Transactional(readOnly = true)
    public List<PrivilegedIdentityDocumentResponse> listIdentityDocuments(UUID customerId) {
        getRequiredCustomer(customerId);
        return documentRepository.findByCustomer_IdOrderByCreatedAtDesc(customerId)
                .stream()
                .map(this::toPrivilegedIdentityResponse)
                .toList();
    }

    @Transactional
    public PrivilegedCustomerKycResponse updateClassification(
            UUID customerId,
            PrivilegedCustomerClassificationRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException("Customer classification update is required.");
        }

        Customer customer = getRequiredCustomer(customerId);
        if (request.category() != null) {
            customer.setCategory(request.category());
        }
        customer.setRiskLevel(request.riskLevel());
        customer.setInternalNotes(normalizeOptionalText(
                request.internalNotes(), MAX_INTERNAL_NOTES_LENGTH, "Internal notes"));
        return toPrivilegedResponse(customerRepository.save(customer));
    }

    private Customer getRequiredCustomer(UUID customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found."));
    }

    private void validateDateOfBirth(LocalDate dateOfBirth) {
        if (dateOfBirth != null && dateOfBirth.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Date of birth cannot be in the future.");
        }
    }

    private void validateIdentityRequest(IdentityDocumentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Identity document is required.");
        }
        if (request.documentType() == null) {
            throw new IllegalArgumentException("Document type is required.");
        }
        normalizeRequiredDisplayText(
                request.documentNumber(), MAX_DOCUMENT_NUMBER_LENGTH, "Document number");
        if (request.issuedDate() != null
                && request.expiryDate() != null
                && request.expiryDate().isBefore(request.issuedDate())) {
            throw new IllegalArgumentException("Expiry date cannot be before issued date.");
        }
    }

    private String normalizeRequiredDisplayText(String value, int maxLength, String fieldName) {
        String normalized = normalizeOptionalText(value, maxLength, fieldName);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return normalized;
    }

    private String normalizeOptionalText(String value, int maxLength, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must not exceed " + maxLength + " characters.");
        }
        return normalized;
    }

    private ReceptionCustomerKycResponse toReceptionResponse(Customer customer) {
        CustomerIdentityDocument primaryDocument = documentRepository
                .findFirstByCustomer_IdAndPrimaryDocumentTrueAndStatus(
                        customer.getId(), IdentityDocumentStatus.ACTIVE)
                .orElse(null);
        CustomerVisitSummaryProjection summary = getVisitSummary(customer.getId());

        return new ReceptionCustomerKycResponse(
                customer.getId(),
                customer.getCustomerCode(),
                customer.getFullName(),
                customer.getPhone(),
                customer.getNationality(),
                customer.getStatus() == null ? null : customer.getStatus().name(),
                customer.getDateOfBirth(),
                customer.getGender(),
                customer.getPermanentAddress(),
                customer.getCurrentAddress(),
                customer.getEmail(),
                customer.getOccupation(),
                customer.getKycStatus(),
                primaryDocument == null ? null : toIdentityResponse(primaryDocument),
                customer.getPrimaryPhotoAttachmentId() != null,
                customer.getPrimaryPhotoAttachmentId(),
                summary == null ? 0 : summary.getTotalVisits(),
                summary == null ? null : summary.getLastVisitBusinessDate(),
                summary == null ? null : summary.getLastEntryTime(),
                summary != null && summary.getHasActiveSession(),
                summary == null ? null : summary.getActiveSessionId()
        );
    }

    private PrivilegedCustomerKycResponse toPrivilegedResponse(Customer customer) {
        List<PrivilegedIdentityDocumentResponse> documents = documentRepository
                .findByCustomer_IdOrderByCreatedAtDesc(customer.getId())
                .stream()
                .map(this::toPrivilegedIdentityResponse)
                .toList();
        List<CustomerAttachmentMetadataResponse> attachments = attachmentRepository
                .findByCustomer_IdOrderByUploadedAtDesc(customer.getId())
                .stream()
                .map(this::toAttachmentResponse)
                .toList();
        CustomerVisitSummaryProjection summary = getVisitSummary(customer.getId());

        return new PrivilegedCustomerKycResponse(
                customer.getId(),
                customer.getCustomerCode(),
                customer.getFullName(),
                customer.getPhone(),
                customer.getNationality(),
                customer.getStatus() == null ? null : customer.getStatus().name(),
                customer.getDateOfBirth(),
                customer.getGender(),
                customer.getPermanentAddress(),
                customer.getCurrentAddress(),
                customer.getEmail(),
                customer.getOccupation(),
                customer.getKycStatus(),
                customer.getCategory(),
                customer.getRiskLevel(),
                customer.getInternalNotes(),
                documents,
                attachments,
                summary == null ? 0 : summary.getTotalVisits(),
                summary == null ? null : summary.getLastVisitBusinessDate(),
                summary == null ? null : summary.getLastEntryTime(),
                summary != null && summary.getHasActiveSession(),
                summary == null ? null : summary.getActiveSessionId(),
                customer.getCreatedAt(),
                customer.getUpdatedAt()
        );
    }

    private IdentityDocumentResponse toIdentityResponse(CustomerIdentityDocument document) {
        return new IdentityDocumentResponse(
                document.getId(),
                document.getDocumentType(),
                document.getDocumentNumber(),
                document.getIssuingCountry(),
                document.getIssuedDate(),
                document.getExpiryDate(),
                document.isPrimaryDocument(),
                document.getStatus()
        );
    }

    private PrivilegedIdentityDocumentResponse toPrivilegedIdentityResponse(
            CustomerIdentityDocument document) {
        return new PrivilegedIdentityDocumentResponse(
                document.getId(),
                document.getDocumentType(),
                document.getDocumentNumber(),
                document.getIssuingCountry(),
                document.getIssuedDate(),
                document.getExpiryDate(),
                document.isPrimaryDocument(),
                document.getStatus(),
                document.getAttachment() == null ? null : toAttachmentResponse(document.getAttachment()),
                toActorResponse(document.getCreatedBy()),
                document.getCreatedAt(),
                toActorResponse(document.getVerifiedBy()),
                document.getVerifiedAt(),
                document.getUpdatedAt()
        );
    }

    private CustomerAttachmentMetadataResponse toAttachmentResponse(CustomerAttachment attachment) {
        return new CustomerAttachmentMetadataResponse(
                attachment.getId(),
                attachment.getAttachmentType(),
                attachment.getOriginalFilename(),
                attachment.getContentType(),
                attachment.getSizeBytes(),
                attachment.getStatus(),
                toActorResponse(attachment.getUploadedBy()),
                attachment.getUploadedAt()
        );
    }

    private ActorReferenceResponse toActorResponse(User user) {
        return user == null ? null : new ActorReferenceResponse(user.getId(), user.getUsername());
    }

    private CustomerVisitSummaryProjection getVisitSummary(UUID customerId) {
        Map<UUID, CustomerVisitSummaryProjection> summaries = sessionRepository
                .findVisitSummariesByCustomerIds(List.of(customerId))
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        CustomerVisitSummaryProjection::getCustomerId,
                        summary -> summary
                ));
        return summaries.get(customerId);
    }
}
