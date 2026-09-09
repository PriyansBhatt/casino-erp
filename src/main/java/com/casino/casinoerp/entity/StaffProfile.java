package com.casino.casinoerp.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "staff_profiles", schema = "casino")
public class StaffProfile {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "employee_code", nullable = false, length = 50) private String employeeCode;
    @Column(name = "department_id", nullable = false) private UUID departmentId;
    @Column(name = "job_title_id", nullable = false) private UUID jobTitleId;
    @Enumerated(EnumType.STRING) @Column(name = "employment_status", nullable = false) private EmploymentStatus employmentStatus;
    @Enumerated(EnumType.STRING) @Column(name = "employment_type", nullable = false) private EmploymentType employmentType;
    @Column(name = "date_of_joining", nullable = false) private LocalDate dateOfJoining;
    @Column(length = 50) private String phone;
    @Column(name = "reporting_manager_staff_profile_id") private UUID reportingManagerStaffProfileId;
    @Column(length = 1000) private String remarks;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
}
