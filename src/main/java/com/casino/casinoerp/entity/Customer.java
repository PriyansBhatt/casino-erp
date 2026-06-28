package com.casino.casinoerp.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "customers", schema = "customer")
public class Customer {

    @Id
    private UUID id;

    @NotBlank(message = "Customer code is required")
    @Column(name = "customer_code")
    private String customerCode;

    @NotBlank(message = "Full name is required")
    @Column(name = "full_name")
    private String fullName;

    @NotBlank(message = "Phone number is required")
    @Pattern(
            regexp = "^[0-9+\\- ]{7,20}$",
            message = "Invalid phone number"
    )
    private String phone;

    @NotBlank(message = "Nationality is required")
    private String nationality;

    @NotBlank(message = "Status is required")
    private String status;
}