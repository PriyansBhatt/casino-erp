package com.casino.casinoerp.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "departments", schema = "casino")
public class Department {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, length = 50) private String code;
    @Column(nullable = false, length = 150) private String name;
    @Column(length = 1000) private String description;
    @Column(nullable = false) private boolean active;
    @Column(name = "sort_order") private Integer sortOrder;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
}
