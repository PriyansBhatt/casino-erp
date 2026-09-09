package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.StaffProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface StaffProfileRepository extends JpaRepository<StaffProfile, UUID> {
    Optional<StaffProfile> findByUserId(UUID userId);
    boolean existsByUserId(UUID userId);
    boolean existsByEmployeeCodeIgnoreCase(String employeeCode);
    List<StaffProfile> findAllByOrderByEmployeeCodeAsc();
}
