package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.StaffProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface StaffProfileRepository extends JpaRepository<StaffProfile, UUID> {
    List<StaffProfile> findByUserIdIn(Collection<UUID> userIds);
    Optional<StaffProfile> findByUserId(UUID userId);
    boolean existsByUserId(UUID userId);
    boolean existsByEmployeeCodeIgnoreCase(String employeeCode);
    List<StaffProfile> findAllByOrderByEmployeeCodeAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select staff from StaffProfile staff where staff.id = :id")
    Optional<StaffProfile> findByIdForUpdate(@Param("id") UUID id);
}
