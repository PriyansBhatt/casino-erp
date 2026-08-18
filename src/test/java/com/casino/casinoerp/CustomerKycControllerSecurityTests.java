package com.casino.casinoerp;

import com.casino.casinoerp.config.JwtAuthenticationFilter;
import com.casino.casinoerp.config.SecurityConfig;
import com.casino.casinoerp.controller.CustomerKycController;
import com.casino.casinoerp.dto.ActorReferenceResponse;
import com.casino.casinoerp.dto.CustomerAttachmentMetadataResponse;
import com.casino.casinoerp.dto.CustomerKycUpdateRequest;
import com.casino.casinoerp.dto.IdentityDocumentRequest;
import com.casino.casinoerp.dto.IdentityDocumentResponse;
import com.casino.casinoerp.dto.PrivilegedCustomerClassificationRequest;
import com.casino.casinoerp.dto.PrivilegedCustomerKycResponse;
import com.casino.casinoerp.dto.PrivilegedIdentityDocumentResponse;
import com.casino.casinoerp.dto.ReceptionCustomerKycResponse;
import com.casino.casinoerp.entity.CustomerAttachmentStatus;
import com.casino.casinoerp.entity.CustomerAttachmentType;
import com.casino.casinoerp.entity.CustomerCategory;
import com.casino.casinoerp.entity.CustomerRiskLevel;
import com.casino.casinoerp.entity.IdentityDocumentStatus;
import com.casino.casinoerp.entity.IdentityDocumentType;
import com.casino.casinoerp.entity.KycStatus;
import com.casino.casinoerp.exception.ResourceConflictException;
import com.casino.casinoerp.exception.ResourceNotFoundException;
import com.casino.casinoerp.service.CustomerKycService;
import com.casino.casinoerp.service.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerKycController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class})
class CustomerKycControllerSecurityTests {

    private static final UUID CUSTOMER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID DOCUMENT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerKycService customerKycService;

    @Autowired
    private JwtService jwtService;

    @ParameterizedTest
    @ValueSource(strings = {"RECEPTIONIST", "DIRECTOR", "SUPER_ADMIN"})
    void operationalRolesCanUseBasicKycEndpoints(String role) throws Exception {
        when(customerKycService.getReceptionKyc(CUSTOMER_ID)).thenReturn(receptionResponse());
        when(customerKycService.updateBasicKyc(eq(CUSTOMER_ID), any(CustomerKycUpdateRequest.class)))
                .thenReturn(receptionResponse());
        when(customerKycService.createIdentityDocument(eq(CUSTOMER_ID), any(IdentityDocumentRequest.class)))
                .thenReturn(identityResponse());

        mockMvc.perform(get("/api/customers/{customerId}/kyc", CUSTOMER_ID)
                        .with(user(role.toLowerCase()).roles(role)))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/customers/{customerId}/kyc", CUSTOMER_ID)
                        .with(user(role.toLowerCase()).roles(role))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(basicKycJson()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/customers/{customerId}/identity-documents", CUSTOMER_ID)
                        .with(user(role.toLowerCase()).roles(role))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(identityJson()))
                .andExpect(status().isCreated());
    }

    @Test
    void realSuperAdminJwtCanAccessUuidShapedBasicKycPath() throws Exception {
        when(customerKycService.getReceptionKyc(CUSTOMER_ID)).thenReturn(receptionResponse());
        String token = jwtService.generateToken("superadmin", "SUPER_ADMIN");

        mockMvc.perform(get("/api/customers/{customerId}/kyc", CUSTOMER_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CUSTOMER_ID.toString()));
    }

    @Test
    void receptionistCannotUsePrivilegedEndpoints() throws Exception {
        mockMvc.perform(get("/api/customers/{customerId}/kyc/privileged", CUSTOMER_ID)
                        .with(user("reception").roles("RECEPTIONIST")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/customers/{customerId}/identity-documents", CUSTOMER_ID)
                        .with(user("reception").roles("RECEPTIONIST")))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/customers/{customerId}/classification", CUSTOMER_ID)
                        .with(user("reception").roles("RECEPTIONIST"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(classificationJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    void cashierCannotUseBasicOrPrivilegedKycEndpoints() throws Exception {
        mockMvc.perform(get("/api/customers/{customerId}/kyc", CUSTOMER_ID)
                        .with(user("cashier").roles("CASHIER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/customers/{customerId}/kyc/privileged", CUSTOMER_ID)
                        .with(user("cashier").roles("CASHIER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/customers/{customerId}/identity-documents", CUSTOMER_ID)
                        .with(user("cashier").roles("CASHIER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/customers/{customerId}/classification", CUSTOMER_ID)
                        .with(user("cashier").roles("CASHIER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(classificationJson()))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DIRECTOR", "SUPER_ADMIN"})
    void privilegedRolesCanUsePrivilegedEndpoints(String role) throws Exception {
        when(customerKycService.getPrivilegedKyc(CUSTOMER_ID)).thenReturn(privilegedResponse());
        when(customerKycService.listIdentityDocuments(CUSTOMER_ID))
                .thenReturn(List.of(privilegedDocumentResponse()));
        when(customerKycService.updateClassification(
                eq(CUSTOMER_ID), any(PrivilegedCustomerClassificationRequest.class)))
                .thenReturn(privilegedResponse());

        mockMvc.perform(get("/api/customers/{customerId}/kyc/privileged", CUSTOMER_ID)
                        .with(user(role.toLowerCase()).roles(role)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/customers/{customerId}/identity-documents", CUSTOMER_ID)
                        .with(user(role.toLowerCase()).roles(role)))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/customers/{customerId}/classification", CUSTOMER_ID)
                        .with(user(role.toLowerCase()).roles(role))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(classificationJson()))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedRequestsAreRejectedForEveryKycEndpoint() throws Exception {
        mockMvc.perform(get("/api/customers/{customerId}/kyc", CUSTOMER_ID))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/customers/{customerId}/kyc", CUSTOMER_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(basicKycJson()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/customers/{customerId}/identity-documents", CUSTOMER_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(identityJson()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/customers/{customerId}/kyc/privileged", CUSTOMER_ID))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/customers/{customerId}/identity-documents", CUSTOMER_ID))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/customers/{customerId}/classification", CUSTOMER_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(classificationJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    void receptionResponseContainsFullPrimaryNumberWithoutPrivilegedFields() throws Exception {
        when(customerKycService.getReceptionKyc(CUSTOMER_ID)).thenReturn(receptionResponse());

        mockMvc.perform(get("/api/customers/{customerId}/kyc", CUSTOMER_ID)
                        .with(user("reception").roles("RECEPTIONIST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryIdentityDocument.documentNumber").value("AB-123 456"))
                .andExpect(jsonPath("$.riskLevel").doesNotExist())
                .andExpect(jsonPath("$.internalNotes").doesNotExist())
                .andExpect(jsonPath("$.primaryIdentityDocument.normalizedDocumentNumber").doesNotExist())
                .andExpect(jsonPath("$.storageKey").doesNotExist())
                .andExpect(jsonPath("$.checksum").doesNotExist())
                .andExpect(jsonPath("$.totalBuyIn").doesNotExist())
                .andExpect(jsonPath("$.gamingProfitability").doesNotExist());
    }

    @Test
    void privilegedResponseContainsApprovedFieldsButNotInternalStorageData() throws Exception {
        when(customerKycService.getPrivilegedKyc(CUSTOMER_ID)).thenReturn(privilegedResponse());

        mockMvc.perform(get("/api/customers/{customerId}/kyc/privileged", CUSTOMER_ID)
                        .with(user("director").roles("DIRECTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("VIP"))
                .andExpect(jsonPath("$.riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.internalNotes").value("Privileged note"))
                .andExpect(jsonPath("$.identityDocuments[0].createdBy.username").value("director"))
                .andExpect(jsonPath("$.identityDocuments[0].documentNumber").value("AB-123 456"))
                .andExpect(jsonPath("$.identityDocuments[0].normalizedDocumentNumber").doesNotExist())
                .andExpect(jsonPath("$.identityDocuments[0].attachment.storageKey").doesNotExist())
                .andExpect(jsonPath("$.identityDocuments[0].attachment.checksum").doesNotExist());
    }

    @Test
    void invalidRequestIsBadRequestAndDuplicateIsConflict() throws Exception {
        mockMvc.perform(post("/api/customers/{customerId}/identity-documents", CUSTOMER_ID)
                        .with(user("reception").roles("RECEPTIONIST"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documentType":null,"documentNumber":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        when(customerKycService.createIdentityDocument(eq(CUSTOMER_ID), any(IdentityDocumentRequest.class)))
                .thenThrow(new ResourceConflictException(
                        "An active identity document with these details already exists."));

        mockMvc.perform(post("/api/customers/{customerId}/identity-documents", CUSTOMER_ID)
                        .with(user("reception").roles("RECEPTIONIST"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(identityJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("An active identity document with these details already exists."));
    }

    @Test
    void missingCustomerReturnsNotFoundEnvelope() throws Exception {
        when(customerKycService.getReceptionKyc(CUSTOMER_ID))
                .thenThrow(new ResourceNotFoundException("Customer not found."));

        mockMvc.perform(get("/api/customers/{customerId}/kyc", CUSTOMER_ID)
                        .with(user("reception").roles("RECEPTIONIST")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Customer not found."));
    }

    @Test
    void classificationRequestDelegatesOnlyApprovedFields() throws Exception {
        when(customerKycService.updateClassification(
                eq(CUSTOMER_ID), any(PrivilegedCustomerClassificationRequest.class)))
                .thenReturn(privilegedResponse());

        mockMvc.perform(patch("/api/customers/{customerId}/classification", CUSTOMER_ID)
                        .with(user("director").roles("DIRECTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(classificationJson()))
                .andExpect(status().isOk());

        verify(customerKycService).updateClassification(
                CUSTOMER_ID,
                new PrivilegedCustomerClassificationRequest(
                        CustomerCategory.VIP,
                        CustomerRiskLevel.HIGH,
                        "Privileged note"
                )
        );
    }

    private ReceptionCustomerKycResponse receptionResponse() {
        return new ReceptionCustomerKycResponse(
                CUSTOMER_ID, "CUS-1001", "Test Customer", "+9779800000000", "Nepali", "ACTIVE",
                LocalDate.of(1990, 1, 1), "Female", "Kathmandu", "Lalitpur",
                "customer@example.com", "Engineer", KycStatus.PENDING, identityResponse(),
                false, null, 6, LocalDate.of(2026, 8, 8),
                LocalDateTime.of(2026, 8, 8, 21, 44), false, null
        );
    }

    private IdentityDocumentResponse identityResponse() {
        return new IdentityDocumentResponse(
                DOCUMENT_ID, IdentityDocumentType.PASSPORT, "AB-123 456", "Nepal",
                LocalDate.of(2020, 1, 1), LocalDate.of(2030, 1, 1), true,
                IdentityDocumentStatus.ACTIVE
        );
    }

    private PrivilegedIdentityDocumentResponse privilegedDocumentResponse() {
        ActorReferenceResponse actor = new ActorReferenceResponse(UUID.randomUUID(), "director");
        CustomerAttachmentMetadataResponse attachment = new CustomerAttachmentMetadataResponse(
                UUID.randomUUID(), CustomerAttachmentType.IDENTITY_DOCUMENT, "passport.pdf",
                "application/pdf", 1024, CustomerAttachmentStatus.ACTIVE, actor,
                LocalDateTime.of(2026, 8, 10, 9, 0)
        );
        return new PrivilegedIdentityDocumentResponse(
                DOCUMENT_ID, IdentityDocumentType.PASSPORT, "AB-123 456", "Nepal",
                LocalDate.of(2020, 1, 1), LocalDate.of(2030, 1, 1), true,
                IdentityDocumentStatus.ACTIVE, attachment, actor,
                LocalDateTime.of(2026, 8, 10, 9, 0), actor,
                LocalDateTime.of(2026, 8, 10, 9, 30), LocalDateTime.of(2026, 8, 10, 9, 30)
        );
    }

    private PrivilegedCustomerKycResponse privilegedResponse() {
        return new PrivilegedCustomerKycResponse(
                CUSTOMER_ID, "CUS-1001", "Test Customer", "+9779800000000", "Nepali", "ACTIVE",
                LocalDate.of(1990, 1, 1), "Female", "Kathmandu", "Lalitpur",
                "customer@example.com", "Engineer", KycStatus.PENDING,
                CustomerCategory.VIP, CustomerRiskLevel.HIGH, "Privileged note",
                List.of(privilegedDocumentResponse()), List.of(), 6,
                LocalDate.of(2026, 8, 8), LocalDateTime.of(2026, 8, 8, 21, 44),
                false, null, LocalDateTime.of(2026, 8, 1, 9, 0),
                LocalDateTime.of(2026, 8, 10, 9, 0)
        );
    }

    private String basicKycJson() {
        return """
                {
                  "dateOfBirth":"1990-01-01",
                  "gender":"Female",
                  "permanentAddress":"Kathmandu",
                  "currentAddress":"Lalitpur",
                  "email":"customer@example.com",
                  "occupation":"Engineer"
                }
                """;
    }

    private String identityJson() {
        return """
                {
                  "documentType":"PASSPORT",
                  "documentNumber":"AB-123 456",
                  "issuingCountry":"Nepal",
                  "issuedDate":"2020-01-01",
                  "expiryDate":"2030-01-01",
                  "primaryDocument":true
                }
                """;
    }

    private String classificationJson() {
        return """
                {"category":"VIP","riskLevel":"HIGH","internalNotes":"Privileged note"}
                """;
    }
}
