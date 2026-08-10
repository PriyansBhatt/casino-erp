package com.casino.casinoerp;

import com.casino.casinoerp.dto.CustomerKycUpdateRequest;
import com.casino.casinoerp.dto.IdentityDocumentRequest;
import com.casino.casinoerp.dto.PrivilegedCustomerKycResponse;
import com.casino.casinoerp.dto.ReceptionCustomerKycResponse;
import com.casino.casinoerp.entity.Customer;
import com.casino.casinoerp.entity.CustomerCategory;
import com.casino.casinoerp.entity.CustomerIdentityDocument;
import com.casino.casinoerp.entity.CustomerRiskLevel;
import com.casino.casinoerp.entity.CustomerStatus;
import com.casino.casinoerp.entity.IdentityDocumentStatus;
import com.casino.casinoerp.entity.IdentityDocumentType;
import com.casino.casinoerp.entity.KycStatus;
import com.casino.casinoerp.entity.User;
import com.casino.casinoerp.exception.ResourceConflictException;
import com.casino.casinoerp.repository.CustomerAttachmentRepository;
import com.casino.casinoerp.repository.CustomerIdentityDocumentRepository;
import com.casino.casinoerp.repository.CustomerRepository;
import com.casino.casinoerp.repository.CustomerSessionRepository;
import com.casino.casinoerp.service.AuthenticatedUserService;
import com.casino.casinoerp.service.CustomerKycService;
import com.casino.casinoerp.service.IdentityDocumentNumberNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerKycServiceTests {

    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final CustomerIdentityDocumentRepository documentRepository =
            mock(CustomerIdentityDocumentRepository.class);
    private final CustomerAttachmentRepository attachmentRepository = mock(CustomerAttachmentRepository.class);
    private final CustomerSessionRepository sessionRepository = mock(CustomerSessionRepository.class);
    private final AuthenticatedUserService authenticatedUserService = mock(AuthenticatedUserService.class);
    private final CustomerKycService service = new CustomerKycService(
            customerRepository,
            documentRepository,
            attachmentRepository,
            sessionRepository,
            new IdentityDocumentNumberNormalizer(),
            authenticatedUserService
    );

    private Customer customer;
    private User actor;

    @BeforeEach
    void setUp() {
        customer = customer();
        actor = new User();
        actor.setId(UUID.randomUUID());
        actor.setUsername("superadmin");

        when(customerRepository.findById(customer.getId())).thenReturn(Optional.of(customer));
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(documentRepository.save(any(CustomerIdentityDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(authenticatedUserService.getRequiredUser()).thenReturn(actor);
    }

    @Test
    void updatesBasicKycAndKeepsPendingStatus() {
        LocalDate dateOfBirth = LocalDate.of(1990, 4, 12);

        ReceptionCustomerKycResponse response = service.updateBasicKyc(
                customer.getId(),
                new CustomerKycUpdateRequest(
                        dateOfBirth,
                        " Female ",
                        " Kathmandu   Nepal ",
                        " Lalitpur ",
                        " customer@example.com ",
                        " Software   Engineer "
                )
        );

        assertThat(customer.getDateOfBirth()).isEqualTo(dateOfBirth);
        assertThat(customer.getGender()).isEqualTo("Female");
        assertThat(customer.getPermanentAddress()).isEqualTo("Kathmandu Nepal");
        assertThat(customer.getCurrentAddress()).isEqualTo("Lalitpur");
        assertThat(customer.getEmail()).isEqualTo("customer@example.com");
        assertThat(customer.getOccupation()).isEqualTo("Software Engineer");
        assertThat(customer.getKycStatus()).isEqualTo(KycStatus.PENDING);
        assertThat(response.kycStatus()).isEqualTo(KycStatus.PENDING);
    }

    @Test
    void normalizesOptionalBlankKycFieldsToNull() {
        customer.setGender("Existing");
        customer.setEmail("existing@example.com");

        service.updateBasicKyc(
                customer.getId(),
                new CustomerKycUpdateRequest(null, "  ", "", "\t", " ", null)
        );

        assertThat(customer.getGender()).isNull();
        assertThat(customer.getPermanentAddress()).isNull();
        assertThat(customer.getCurrentAddress()).isNull();
        assertThat(customer.getEmail()).isNull();
        assertThat(customer.getOccupation()).isNull();
    }

    @Test
    void rejectsFutureDateOfBirthBeforeChangingCustomer() {
        LocalDate original = LocalDate.of(1990, 1, 1);
        customer.setDateOfBirth(original);

        assertThatThrownBy(() -> service.updateBasicKyc(
                customer.getId(),
                new CustomerKycUpdateRequest(LocalDate.now().plusDays(1), null, null, null, null, null)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Date of birth cannot be in the future.");

        assertThat(customer.getDateOfBirth()).isEqualTo(original);
        verify(customerRepository, never()).save(any());
    }

    @Test
    void rejectsInvalidEmail() {
        assertThatThrownBy(() -> service.updateBasicKyc(
                customer.getId(),
                new CustomerKycUpdateRequest(null, null, null, null, "invalid-email", null)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid email address.");
    }

    @Test
    void createsActiveDocumentWithNormalizedNumberAndAuthenticatedActor() {
        IdentityDocumentRequest request = request(" ab-123 456 ", false);

        service.createIdentityDocument(customer.getId(), request);

        ArgumentCaptor<CustomerIdentityDocument> captor =
                ArgumentCaptor.forClass(CustomerIdentityDocument.class);
        verify(documentRepository).save(captor.capture());
        CustomerIdentityDocument saved = captor.getValue();
        assertThat(saved.getCustomer()).isSameAs(customer);
        assertThat(saved.getDocumentNumber()).isEqualTo("ab-123 456");
        assertThat(saved.getNormalizedDocumentNumber()).isEqualTo("AB123456");
        assertThat(saved.getStatus()).isEqualTo(IdentityDocumentStatus.ACTIVE);
        assertThat(saved.isPrimaryDocument()).isFalse();
        assertThat(saved.getCreatedBy()).isSameAs(actor);
    }

    @Test
    void rejectsDuplicateActiveDocumentWithoutMutatingExistingPrimary() {
        CustomerIdentityDocument existingPrimary = existingPrimary();
        when(documentRepository.existsActiveDuplicate(
                IdentityDocumentType.PASSPORT,
                "Nepal",
                "AB123",
                IdentityDocumentStatus.ACTIVE
        )).thenReturn(true);

        assertThatThrownBy(() -> service.createIdentityDocument(
                customer.getId(), request("AB-123", true)
        )).isInstanceOf(ResourceConflictException.class)
                .hasMessage("An active identity document with these details already exists.");

        assertThat(existingPrimary.getStatus()).isEqualTo(IdentityDocumentStatus.ACTIVE);
        assertThat(existingPrimary.isPrimaryDocument()).isTrue();
        verify(documentRepository, never()).findFirstByCustomer_IdAndPrimaryDocumentTrueAndStatus(any(), any());
        verify(documentRepository, never()).save(any());
        verify(documentRepository, never()).saveAndFlush(any());
    }

    @Test
    void nonActiveHistoricalDocumentDoesNotPreventNewActiveDocument() {
        when(documentRepository.existsActiveDuplicate(
                IdentityDocumentType.PASSPORT,
                "Nepal",
                "AB123",
                IdentityDocumentStatus.ACTIVE
        )).thenReturn(false);

        service.createIdentityDocument(customer.getId(), request("AB-123", false));

        verify(documentRepository).save(any(CustomerIdentityDocument.class));
    }

    @Test
    void rejectsInvalidDocumentDateOrderBeforePrimaryMutation() {
        CustomerIdentityDocument existingPrimary = existingPrimary();
        IdentityDocumentRequest invalid = new IdentityDocumentRequest(
                IdentityDocumentType.PASSPORT,
                "AB-123",
                "Nepal",
                LocalDate.of(2030, 1, 1),
                LocalDate.of(2029, 1, 1),
                true
        );

        assertThatThrownBy(() -> service.createIdentityDocument(customer.getId(), invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Expiry date cannot be before issued date.");

        assertThat(existingPrimary.getStatus()).isEqualTo(IdentityDocumentStatus.ACTIVE);
        assertThat(existingPrimary.isPrimaryDocument()).isTrue();
        verify(documentRepository, never()).save(any());
        verify(documentRepository, never()).saveAndFlush(any());
    }

    @Test
    void newPrimaryReplacesExistingPrimaryWithoutDeletingHistory() {
        CustomerIdentityDocument existingPrimary = existingPrimary();
        when(documentRepository.findFirstByCustomer_IdAndPrimaryDocumentTrueAndStatus(
                customer.getId(), IdentityDocumentStatus.ACTIVE
        )).thenReturn(Optional.of(existingPrimary));

        service.createIdentityDocument(customer.getId(), request("NEW-456", true));

        assertThat(existingPrimary.getStatus()).isEqualTo(IdentityDocumentStatus.REPLACED);
        assertThat(existingPrimary.isPrimaryDocument()).isFalse();
        verify(documentRepository).saveAndFlush(existingPrimary);
        verify(documentRepository).save(any(CustomerIdentityDocument.class));
        verify(documentRepository, never()).delete(any());
    }

    @Test
    void responseTypesEnforceReceptionAndPrivilegedBoundaries() {
        customer.setRiskLevel(CustomerRiskLevel.HIGH);
        customer.setInternalNotes("Privileged note");
        when(documentRepository.findByCustomer_IdOrderByCreatedAtDesc(customer.getId()))
                .thenReturn(List.of());
        when(attachmentRepository.findByCustomer_IdOrderByUploadedAtDesc(customer.getId()))
                .thenReturn(List.of());

        ReceptionCustomerKycResponse reception = service.getReceptionKyc(customer.getId());
        PrivilegedCustomerKycResponse privileged = service.getPrivilegedKyc(customer.getId());

        assertThat(reception.getClass().getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("riskLevel", "internalNotes", "financialValues", "gamingProfitability", "serviceCost");
        assertThat(privileged.riskLevel()).isEqualTo(CustomerRiskLevel.HIGH);
        assertThat(privileged.internalNotes()).isEqualTo("Privileged note");
        assertThat(privileged.getClass().getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("financialValues", "gamingProfitability", "serviceCost");
    }

    private Customer customer() {
        Customer value = new Customer();
        value.setId(UUID.randomUUID());
        value.setCustomerCode("CUS-1001");
        value.setFullName("Test Customer");
        value.setPhone("+9779800000000");
        value.setNationality("Nepali");
        value.setStatus(CustomerStatus.ACTIVE);
        value.setCategory(CustomerCategory.NORMAL);
        value.setKycStatus(KycStatus.PENDING);
        return value;
    }

    private IdentityDocumentRequest request(String number, boolean primary) {
        return new IdentityDocumentRequest(
                IdentityDocumentType.PASSPORT,
                number,
                " Nepal ",
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2035, 1, 1),
                primary
        );
    }

    private CustomerIdentityDocument existingPrimary() {
        CustomerIdentityDocument document = new CustomerIdentityDocument();
        document.setId(UUID.randomUUID());
        document.setCustomer(customer);
        document.setDocumentType(IdentityDocumentType.CITIZENSHIP);
        document.setDocumentNumber("OLD-123");
        document.setNormalizedDocumentNumber("OLD123");
        document.setPrimaryDocument(true);
        document.setStatus(IdentityDocumentStatus.ACTIVE);
        document.setCreatedBy(actor);
        return document;
    }
}
