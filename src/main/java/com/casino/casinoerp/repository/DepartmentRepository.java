package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {
    boolean existsByCodeIgnoreCase(String code);
    List<Department> findAllByOrderBySortOrderAscNameAsc();
}
