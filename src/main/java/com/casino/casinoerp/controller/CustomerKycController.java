package com.casino.casinoerp.controller;

import com.casino.casinoerp.dto.CustomerKycUpdateRequest;
import com.casino.casinoerp.dto.IdentityDocumentRequest;
import com.casino.casinoerp.dto.IdentityDocumentResponse;
import com.casino.casinoerp.dto.PrivilegedCustomerClassificationRequest;
import com.casino.casinoerp.dto.PrivilegedCustomerKycResponse;
import com.casino.casinoerp.dto.PrivilegedIdentityDocumentResponse;
import com.casino.casinoerp.dto.ReceptionCustomerKycResponse;
import com.casino.casinoerp.service.CustomerKycService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/customers/{customerId}")
public class CustomerKycController {

    private final CustomerKycService customerKycService;

    public CustomerKycController(CustomerKycService customerKycService) {
        this.customerKycService = customerKycService;
    }

    @GetMapping("/kyc")
    public ReceptionCustomerKycResponse getReceptionKyc(@PathVariable UUID customerId) {
        return customerKycService.getReceptionKyc(customerId);
    }

    @PatchMapping("/kyc")
    public ReceptionCustomerKycResponse updateBasicKyc(
            @PathVariable UUID customerId,
            @Valid @RequestBody CustomerKycUpdateRequest request
    ) {
        return customerKycService.updateBasicKyc(customerId, request);
    }

    @PostMapping("/identity-documents")
    @ResponseStatus(HttpStatus.CREATED)
    public IdentityDocumentResponse createIdentityDocument(
            @PathVariable UUID customerId,
            @Valid @RequestBody IdentityDocumentRequest request
    ) {
        return customerKycService.createIdentityDocument(customerId, request);
    }

    @GetMapping("/kyc/privileged")
    public PrivilegedCustomerKycResponse getPrivilegedKyc(@PathVariable UUID customerId) {
        return customerKycService.getPrivilegedKyc(customerId);
    }

    @GetMapping("/identity-documents")
    public List<PrivilegedIdentityDocumentResponse> getIdentityDocuments(
            @PathVariable UUID customerId
    ) {
        return customerKycService.listIdentityDocuments(customerId);
    }

    @PatchMapping("/classification")
    public PrivilegedCustomerKycResponse updateClassification(
            @PathVariable UUID customerId,
            @Valid @RequestBody PrivilegedCustomerClassificationRequest request
    ) {
        return customerKycService.updateClassification(customerId, request);
    }
}
