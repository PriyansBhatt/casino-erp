package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, UUID> {
    boolean existsByCodeIgnoreCase(String code);
    List<LeaveType> findAllByOrderByNameAsc();
}
