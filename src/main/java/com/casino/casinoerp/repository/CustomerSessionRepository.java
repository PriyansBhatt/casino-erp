package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.CustomerSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CustomerSessionRepository extends JpaRepository<CustomerSession, UUID> {
}