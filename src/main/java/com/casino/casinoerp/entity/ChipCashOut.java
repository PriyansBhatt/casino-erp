package com.casino.casinoerp.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.persistence.GeneratedValue;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Entity
@Table(name = "chip_cash_outs", schema = "cashier")
public class ChipCashOut {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank(message = "Cash out code is required")
    @Column(name = "cash_out_code")
    private String cashOutCode;

    @NotNull(message = "Customer session ID is required")
    @Column(name = "customer_session_id")
    private UUID customerSessionId;

    @NotNull(message = "Customer ID is required")
    @Column(name = "customer_id")
    private UUID customerId;

    @NotNull(message = "Total chip value returned is required")
    @DecimalMin(value = "1.00", message = "Total chip value returned must be greater than 0")
    @Column(name = "total_chip_value_returned")
    private BigDecimal totalChipValueReturned;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "chip_cash_out_denominations", schema = "cashier",
            joinColumns = @JoinColumn(name = "chip_cash_out_id"))
    @MapKeyColumn(name = "denomination")
    @Column(name = "quantity", nullable = false)
    private Map<Integer, Long> denominations = new LinkedHashMap<>();

    @NotNull(message = "Cash paid is required")
    @DecimalMin(value = "1.00", message = "Cash paid must be greater than 0")
    @Column(name = "cash_paid")
    private BigDecimal cashPaid;

    @Column(name = "payment_mode")
    private String paymentMode;

    @Column(name = "payment_reference")
    private String paymentReference;

    @NotNull(message = "Same customer verification is required")
    @Column(name = "same_customer_verified")
    private Boolean sameCustomerVerified;

    @NotNull(message = "Third party attempt status is required")
    @Column(name = "third_party_attempt")
    private Boolean thirdPartyAttempt;

    @Column(name = "badge_assignment_id")
    private UUID badgeAssignmentId;

    @Column(name = "cashier_shift_id")
    private UUID cashierShiftId;

    @Column(name = "supervisor_approved_by")
    private UUID supervisorApprovedBy;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "business_date")
    private LocalDate businessDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "idempotency_key")
    @JsonIgnore
    private String idempotencyKey;

    private String remarks;
}
