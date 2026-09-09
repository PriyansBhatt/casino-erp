package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.JobTitle;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface JobTitleRepository extends JpaRepository<JobTitle, UUID> {
    boolean existsByCodeIgnoreCase(String code);
    List<JobTitle> findAllByOrderByNameAsc();
}
