package com.casino.casinoerp.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Entity
@Table(name = "chip_buy_ins", schema = "cashier")
public class ChipBuyIn implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private boolean newEntity = true;

    @NotBlank(message = "Buy-in code is required")
    @Column(name = "buy_in_code")
    private String buyInCode;

    @NotNull(message = "Customer session ID is required")
    @Column(name = "customer_session_id")
    private UUID customerSessionId;

    @NotNull(message = "Customer ID is required")
    @Column(name = "customer_id")
    private UUID customerId;

    @NotNull(message = "Amount received is required")
    @DecimalMin(value = "0.01", message = "Amount received must be greater than 0")
    @Column(name = "amount_received")
    private BigDecimal amountReceived;

    @NotBlank(message = "Payment mode is required")
    @Column(name = "payment_mode")
    private String paymentMode;

    @Column(name = "payment_reference")
    private String paymentReference;

    @NotNull(message = "Total chip value issued is required")
    @DecimalMin(value = "0.01", message = "Total chip value issued must be greater than 0")
    @Column(name = "total_chip_value_issued")
    private BigDecimal totalChipValueIssued;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "chip_buy_in_denominations", schema = "cashier",
            joinColumns = @JoinColumn(name = "chip_buy_in_id"))
    @MapKeyColumn(name = "denomination")
    @Column(name = "quantity", nullable = false)
    private Map<Integer, Long> denominations = new LinkedHashMap<>();

    @Column(name = "high_value_alert")
    private Boolean highValueAlert;

    @Column(name = "supervisor_approved_by")
    private UUID supervisorApprovedBy;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "business_date")
    private LocalDate businessDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    private String remarks;

    @Override
    @JsonIgnore
    public boolean isNew() {
        return newEntity;
    }

    @PostLoad
    @PostPersist
    private void markNotNew() {
        newEntity = false;
    }
}
