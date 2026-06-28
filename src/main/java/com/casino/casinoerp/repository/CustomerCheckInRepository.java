package com.casino.casinoerp.repository;

import com.casino.casinoerp.entity.CustomerCheckIn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CustomerCheckInRepository extends JpaRepository<CustomerCheckIn, UUID> {
}