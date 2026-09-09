package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.ShiftDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ShiftDefinitionRepository extends JpaRepository<ShiftDefinition,UUID> {
    boolean existsByCodeIgnoreCase(String code);
    List<ShiftDefinition> findAllByOrderByNameAsc();
}
